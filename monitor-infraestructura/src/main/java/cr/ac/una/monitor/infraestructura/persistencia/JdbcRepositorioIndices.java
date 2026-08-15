package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia de Isbd en monitor_indices (V1, con las columnas nullable de
 * V5 -- ver esa migración sobre por qué ip/im/ia/calibracion_id ya no son
 * NOT NULL).
 *
 * Al leer, el Indicador reconstruido para ip/im/ia trae puntuacionesPorVariable
 * vacío: monitor_indices solo guarda el agregado, no el desglose por variable
 * (eso vive en monitor_procesos/memoria/archivos, correlacionable por
 * timestamp si hace falta) -- simplificación documentada, no un olvido.
 */
@Repository
public class JdbcRepositorioIndices implements RepositorioIndices {

    private static final String SEPARADOR_CAUSAS = " | ";

    private final JdbcClient jdbc;

    public JdbcRepositorioIndices(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public void guardar(InstanciaId instancia, Isbd isbd) {
        jdbc.sql("""
                INSERT INTO monitor_indices
                    (instancia_id, calculado_en, ip, im, ia, isbd, estado, estado_por_veto, parcial, causas)
                VALUES (:instancia_id, :calculado_en, :ip, :im, :ia, :isbd, :estado, :estado_por_veto,
                        :parcial, :causas)
                """)
            .param("instancia_id", instancia.valor())
            .param("calculado_en", OffsetDateTime.ofInstant(isbd.momento(), ZoneOffset.UTC))
            .param("ip", isbd.ip().map(Indicador::puntuacion).orElse(null))
            .param("im", isbd.im().map(Indicador::puntuacion).orElse(null))
            .param("ia", isbd.ia().map(Indicador::puntuacion).orElse(null))
            .param("isbd", isbd.puntuacion())
            .param("estado", isbd.estado().name())
            .param("estado_por_veto", isbd.estadoPorVeto())
            .param("parcial", isbd.parcial())
            .param("causas", isbd.causas().isEmpty() ? null : String.join(SEPARADOR_CAUSAS, isbd.causas()))
            .update();
    }

    @Override
    public Optional<Isbd> ultimo(InstanciaId instancia) {
        return jdbc.sql("SELECT * FROM monitor_indices WHERE instancia_id = :instancia_id "
                + "ORDER BY calculado_en DESC LIMIT 1")
            .param("instancia_id", instancia.valor())
            .query(this::mapear)
            .optional();
    }

    @Override
    public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
        return jdbc.sql("SELECT * FROM monitor_indices WHERE instancia_id = :instancia_id "
                + "AND calculado_en BETWEEN :desde AND :hasta ORDER BY calculado_en ASC")
            .param("instancia_id", instancia.valor())
            .param("desde", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC))
            .param("hasta", OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC))
            .query(this::mapear)
            .list();
    }

    private Isbd mapear(ResultSet rs, int rowNum) throws SQLException {
        Instant calculadoEn = rs.getObject("calculado_en", OffsetDateTime.class).toInstant();
        Optional<Indicador> ip = indicadorONulo(rs, "ip", Componente.PROCESOS);
        Optional<Indicador> im = indicadorONulo(rs, "im", Componente.MEMORIA);
        Optional<Indicador> ia = indicadorONulo(rs, "ia", Componente.ARCHIVOS);
        double puntuacion = rs.getDouble("isbd");
        Estado estado = Estado.valueOf(rs.getString("estado"));
        boolean estadoPorVeto = rs.getBoolean("estado_por_veto");
        boolean parcial = rs.getBoolean("parcial");
        String causasCrudas = rs.getString("causas");
        List<String> causas = (causasCrudas == null || causasCrudas.isBlank())
            ? List.of() : Arrays.asList(causasCrudas.split(" \\| "));

        return new Isbd(calculadoEn, puntuacion, estado, ip, im, ia, estadoPorVeto, causas, parcial);
    }

    private Optional<Indicador> indicadorONulo(ResultSet rs, String columna, Componente componente)
            throws SQLException {
        double valor = rs.getDouble(columna);
        if (rs.wasNull()) {
            return Optional.empty();
        }
        return Optional.of(new Indicador(componente, valor, Map.of()));
    }
}
