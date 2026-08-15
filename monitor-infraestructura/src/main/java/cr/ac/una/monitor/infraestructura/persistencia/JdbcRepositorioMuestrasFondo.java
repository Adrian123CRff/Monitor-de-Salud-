package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
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
 * Persistencia del histórico crudo de procesos de fondo (ADR 0002), en la
 * tabla dedicada monitor_procesos_fondo (V3) -- ver RepositorioMuestrasFondo
 * sobre por qué no comparte tabla ni puerto con RepositorioMuestras.
 */
@Repository
public class JdbcRepositorioMuestrasFondo implements RepositorioMuestrasFondo {

    private static final Set<String> COLUMNAS_NO_VARIABLES = Set.of("id", "instancia_id", "muestreado_en");

    private static final Set<String> COLUMNAS_PERSISTIBLES = Set.of(
        "b1_procesos_caidos",
        "b2_lgwr_time_waited_acum", "b2_lgwr_total_waits_acum", "b2_lgwr_espera_avg",
        "b3_dbwr_time_waited_acum", "b3_dbwr_total_waits_acum", "b3_dbwr_espera_avg",
        "b4_ckpt_switch_incompleto_acum", "b4_ckpt_switch_incompleto");

    private final JdbcClient jdbc;

    public JdbcRepositorioMuestrasFondo(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public void guardar(InstanciaId instancia, Muestra muestra) {
        List<String> columnas = muestra.valores().keySet().stream()
            .filter(COLUMNAS_PERSISTIBLES::contains)
            .toList();
        List<String> todasColumnas = new ArrayList<>(columnas);
        todasColumnas.add("instancia_reiniciada");

        String columnasSql = String.join(", ", todasColumnas);
        String placeholders = todasColumnas.stream().map(c -> ":" + c).collect(Collectors.joining(", "));

        var spec = jdbc.sql("INSERT INTO monitor_procesos_fondo (instancia_id, muestreado_en, " + columnasSql + ") "
                + "VALUES (:instancia_id, :muestreado_en, " + placeholders + ")")
            .param("instancia_id", instancia.valor())
            .param("muestreado_en", OffsetDateTime.ofInstant(muestra.momento(), ZoneOffset.UTC));
        for (String columna : columnas) {
            spec = spec.param(columna, muestra.valores().get(columna));
        }
        spec = spec.param("instancia_reiniciada", muestra.instanciaReiniciada());
        spec.update();
    }

    @Override
    public Optional<Muestra> ultima(InstanciaId instancia) {
        return jdbc.sql("SELECT * FROM monitor_procesos_fondo WHERE instancia_id = :instancia_id "
                + "ORDER BY muestreado_en DESC LIMIT 1")
            .param("instancia_id", instancia.valor())
            .query(this::mapearFila)
            .optional();
    }

    private Muestra mapearFila(ResultSet rs, int rowNum) throws SQLException {
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
        return new Muestra(Componente.PROCESOS, momento, valores, reiniciada);
    }
}
