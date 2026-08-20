package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistencia del histórico crudo (ADR 0002: PostgreSQL separado de la
 * instancia observada, y modelo-datos.md decisión #2: solo se guardan
 * valores crudos, no puntuaciones derivadas).
 *
 * Los recolectores meten en Muestra.valores() tanto columnas crudas
 * (p1_procesos_actuales...) como variables derivadas para el cálculo del
 * índice. COLUMNAS_PERSISTIBLES es la lista blanca por componente que filtra
 * cuáles sí se guardan -- sin ella, guardar() intentaría insertar en
 * columnas que no existen.
 *
 * Las derivadas que PUNTÚAN ya no se pierden (V9): las que implican un
 * cálculo real tienen columna propia, y las dos de archivos que son alias
 * exactos de una columna cruda se reexponen al leer (ver ALIAS_AL_LEER).
 * Antes se filtraban todas, así que una muestra leída de vuelta puntuaba
 * distinto que cuando se tomó -- ARCHIVOS daba 100 en el detalle mientras el
 * ISBD del mismo ciclo decía 90 -- y el Módulo B se quedaba sin la
 * distribución histórica que necesita para calibrar por percentiles.
 *
 * enRango() no pagina ni resume por granularidad: el puerto no pide eso
 * (List<Muestra> plano), y no hay todavía un endpoint de histórico que
 * necesite más -- se amplía cuando haga falta, no antes.
 */
@Repository
public class JdbcRepositorioMuestras implements RepositorioMuestras {

    private static final Set<String> COLUMNAS_NO_VARIABLES = Set.of("id", "instancia_id", "muestreado_en");

    private static final Map<Componente, Set<String>> COLUMNAS_PERSISTIBLES = Map.of(
        Componente.PROCESOS, Set.of(
            "p1_procesos_actuales", "p2_procesos_maximos", "p3_sesiones_actuales",
            "p4_sesiones_activas", "p5_sesiones_inactivas", "p6_sesiones_bloqueadas",
            "p7_operaciones_largas", "p8_peor_util_recurso",
            "limite_procesos", "limite_sesiones", "bloqueo_max_seg",
            "util_procesos_pct", "util_sesiones_pct"),
        Componente.MEMORIA, Set.of(
            "m1_sga_total_bytes", "m2_sga_libre_bytes", "m3_shared_pool_bytes",
            "m4_buffer_cache_bytes", "m5_pga_asignada_bytes", "m6_pga_en_uso_bytes",
            "m7_pga_maxima_bytes", "m8_over_alloc_acum", "m8_over_alloc_delta",
            "m9_cache_hit_pct", "m10_multipass_acum", "m10_multipass_delta", "pga_target_bytes",
            "pga_uso_pct"),
        Componente.ARCHIVOS, Set.of(
            "a1_datafiles_online", "a2_datafiles_offline", "a3_datafiles_bytes",
            "a4_peor_tablespace_pct", "a4_tablespaces_riesgo",
            "a5_tempfiles_online", "a5_tempfiles_bytes",
            "a6_grupos_redo", "a6_min_miembros_grupo",
            "a7_archivos_invalidos", "a8_archivos_recover"));

    /**
     * Derivadas que NO son un cálculo sino otro nombre para una columna que ya
     * existe (ver JdbcRecolectorArchivos: "son el mismo valor, dos claves").
     * Persistirlas sería guardar el dato dos veces; se reconstruyen al leer
     * para que la muestra que sale sea idéntica a la que entró.
     */
    private static final Map<String, String> ALIAS_AL_LEER = Map.of(
        "a4_peor_tablespace_pct", "peor_tablespace_pct",
        "a6_min_miembros_grupo", "redundancia_redo");

    private final JdbcClient jdbc;

    public JdbcRepositorioMuestras(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public void guardar(InstanciaId instancia, Muestra muestra) {
        String tabla = tabla(muestra.componente());
        Set<String> permitidas = COLUMNAS_PERSISTIBLES.get(muestra.componente());
        List<String> columnas = muestra.valores().keySet().stream()
            .filter(permitidas::contains)
            .toList();

        // instancia_reiniciada es un boolean del propio record Muestra, no
        // vive en el mapa valores() -- solo la tabla de memoria tiene esta columna.
        boolean incluyeReiniciada = muestra.componente() == Componente.MEMORIA;
        List<String> todasColumnas = new ArrayList<>(columnas);
        if (incluyeReiniciada) {
            todasColumnas.add("instancia_reiniciada");
        }

        String columnasSql = String.join(", ", todasColumnas);
        String placeholders = todasColumnas.stream().map(c -> ":" + c).collect(Collectors.joining(", "));

        var spec = jdbc.sql("INSERT INTO " + tabla + " (instancia_id, muestreado_en, " + columnasSql + ") "
                + "VALUES (:instancia_id, :muestreado_en, " + placeholders + ")")
            .param("instancia_id", instancia.valor())
            .param("muestreado_en", OffsetDateTime.ofInstant(muestra.momento(), ZoneOffset.UTC));
        for (String columna : columnas) {
            spec = spec.param(columna, muestra.valores().get(columna));
        }
        if (incluyeReiniciada) {
            spec = spec.param("instancia_reiniciada", muestra.instanciaReiniciada());
        }
        spec.update();
    }

    @Override
    public Optional<Muestra> ultima(InstanciaId instancia, Componente componente) {
        String tabla = tabla(componente);
        return jdbc.sql("SELECT * FROM " + tabla + " WHERE instancia_id = :instancia_id "
                + "ORDER BY muestreado_en DESC LIMIT 1")
            .param("instancia_id", instancia.valor())
            .query((rs, rowNum) -> mapearFila(rs, componente))
            .optional();
    }

    @Override
    public List<Muestra> enRango(InstanciaId instancia, Componente componente, Instant desde, Instant hasta) {
        String tabla = tabla(componente);
        return jdbc.sql("SELECT * FROM " + tabla + " WHERE instancia_id = :instancia_id "
                + "AND muestreado_en BETWEEN :desde AND :hasta "
                + "ORDER BY muestreado_en ASC")
            .param("instancia_id", instancia.valor())
            .param("desde", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC))
            .param("hasta", OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC))
            .query((rs, rowNum) -> mapearFila(rs, componente))
            .list();
    }

    @Override
    public List<Muestra> ultimasN(InstanciaId instancia, Componente componente, int n) {
        String tabla = tabla(componente);
        return jdbc.sql("SELECT * FROM " + tabla + " WHERE instancia_id = :instancia_id "
                + "ORDER BY muestreado_en DESC LIMIT :n")
            .param("instancia_id", instancia.valor())
            .param("n", n)
            .query((rs, rowNum) -> mapearFila(rs, componente))
            .list();
    }

    private Muestra mapearFila(ResultSet rs, Componente componente) throws SQLException {
        Map<String, Double> valores = new HashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        Instant momento = null;
        boolean reiniciada = false;

        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String columna = meta.getColumnLabel(i).toLowerCase();
            if (columna.equals("muestreado_en")) {
                momento = rs.getObject(i, OffsetDateTime.class).toInstant();
                continue;
            }
            if (columna.equals("instancia_reiniciada")) {
                reiniciada = rs.getBoolean(i);
                continue;
            }
            if (COLUMNAS_NO_VARIABLES.contains(columna)) {
                continue;
            }
            double valor = rs.getDouble(i);
            if (!rs.wasNull()) {
                valores.put(columna, valor);
            }
        }
        ALIAS_AL_LEER.forEach((columna, derivada) -> {
            Double valor = valores.get(columna);
            if (valor != null) {
                valores.put(derivada, valor);
            }
        });

        return new Muestra(componente, momento, valores, reiniciada);
    }

    private String tabla(Componente componente) {
        return switch (componente) {
            case PROCESOS -> "monitor_procesos";
            case MEMORIA -> "monitor_memoria";
            case ARCHIVOS -> "monitor_archivos";
        };
    }
}
