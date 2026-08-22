package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioAlertas;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.alertas.Nivel;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Persistencia de episodios de alerta en monitor_alertas (V1, ver Alerta / MotorAlertas). */
@Repository
public class JdbcRepositorioAlertas implements RepositorioAlertas {

    private final JdbcClient jdbc;

    public JdbcRepositorioAlertas(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Optional<Alerta> buscarAbierta(InstanciaId instancia, String variable, Optional<String> entidad) {
        var spec = jdbc.sql("""
                SELECT * FROM monitor_alertas
                WHERE instancia_id = :instancia_id AND variable = :variable AND cerrada_en IS NULL
                AND entidad IS NOT DISTINCT FROM :entidad
                """)
            .param("instancia_id", instancia.valor())
            .param("variable", variable)
            .param("entidad", entidad.orElse(null));
        return spec.query(this::mapear).optional();
    }

    @Override
    public Alerta abrir(Alerta nueva) {
        Long id = jdbc.sql("""
                INSERT INTO monitor_alertas
                    (instancia_id, abierta_en, componente, variable, entidad, valor, umbral, nivel, descripcion)
                VALUES (:instancia_id, :abierta_en, :componente, :variable, :entidad, :valor, :umbral, :nivel,
                        :descripcion)
                RETURNING id
                """)
            .param("instancia_id", nueva.instancia().valor())
            .param("abierta_en", OffsetDateTime.ofInstant(nueva.abiertaEn(), ZoneOffset.UTC))
            .param("componente", nueva.componente().name())
            .param("variable", nueva.variable())
            .param("entidad", nueva.entidad().orElse(null))
            .param("valor", nueva.valor())
            .param("umbral", nueva.umbral())
            .param("nivel", nueva.nivel().name())
            .param("descripcion", nueva.descripcion())
            .query(Long.class)
            .single();

        return new Alerta(id, nueva.instancia(), nueva.componente(), nueva.variable(), nueva.entidad(),
            nueva.nivel(), nueva.valor(), nueva.umbral(), nueva.descripcion(), nueva.abiertaEn(),
            nueva.cerradaEn());
    }

    @Override
    public void cerrar(Alerta existente, java.time.Instant cerradaEn) {
        jdbc.sql("UPDATE monitor_alertas SET cerrada_en = :cerrada_en WHERE id = :id")
            .param("cerrada_en", OffsetDateTime.ofInstant(cerradaEn, ZoneOffset.UTC))
            .param("id", existente.id())
            .update();
    }

    @Override
    public List<Alerta> abiertas(InstanciaId instancia) {
        // Orden de severidad explícito en Java, no en SQL: ordenar la columna nivel
        // (VARCHAR) alfabéticamente DESC da por casualidad CRITICO > ALTO > ADVERTENCIA
        // hoy, pero es frágil (se rompe con un nivel nuevo que no siga ese orden
        // alfabético) -- ver skill diseno-de-indicadores, "Prioridad de presentación".
        List<Alerta> abiertas = jdbc.sql("SELECT * FROM monitor_alertas WHERE instancia_id = :instancia_id "
                + "AND cerrada_en IS NULL")
            .param("instancia_id", instancia.valor())
            .query(this::mapear)
            .list();

        Comparator<Alerta> porSeveridadLuegoDuracion = Comparator
            .comparingInt((Alerta a) -> a.nivel().ordinal()).reversed()
            .thenComparing(Alerta::abiertaEn);
        return abiertas.stream().sorted(porSeveridadLuegoDuracion).toList();
    }

    @Override
    public List<Alerta> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
        // Interseccion de intervalos: abrio antes de que la ventana termine, y
        // cerro despues de que empiece (o sigue abierto). Ordenado por apertura
        // porque quien lo consume dibuja una linea de tiempo.
        return jdbc.sql("""
                SELECT * FROM monitor_alertas
                WHERE instancia_id = :instancia_id
                  AND abierta_en <= :hasta
                  AND (cerrada_en IS NULL OR cerrada_en >= :desde)
                ORDER BY abierta_en
                """)
            .param("instancia_id", instancia.valor())
            .param("desde", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC))
            .param("hasta", OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC))
            .query(this::mapear)
            .list();
    }

    private Alerta mapear(ResultSet rs, int rowNum) throws SQLException {
        String entidad = rs.getString("entidad");
        OffsetDateTime cerradaEn = rs.getObject("cerrada_en", OffsetDateTime.class);

        return new Alerta(
            rs.getLong("id"),
            new InstanciaId(rs.getLong("instancia_id")),
            Componente.valueOf(rs.getString("componente")),
            rs.getString("variable"),
            Optional.ofNullable(entidad),
            Nivel.valueOf(rs.getString("nivel")),
            rs.getDouble("valor"),
            rs.getDouble("umbral"),
            rs.getString("descripcion"),
            rs.getObject("abierta_en", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(cerradaEn).map(OffsetDateTime::toInstant));
    }
}
