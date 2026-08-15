package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Persistencia de Calibracion en monitor_calibracion (V4 añade
 * veto_habilitado/umbral_veto_componente sobre el esquema de V1).
 *
 * Solo cubre pesos + veto -- Calibracion no incluye la lista de Umbral por
 * variable (eso sigue siendo UmbralesIniciales, código, no datos).
 * monitor_umbral queda sin usar hasta que el dominio soporte calibrar
 * umbrales por variable, no solo los tres pesos de IP/IM/IA.
 *
 * registrar() no recibe nombre/justificación (Calibracion no los tiene) --
 * se genera un nombre con la marca de tiempo; ponerle un nombre elegido por
 * quien recalibra es trabajo del endpoint de recalibración, que todavía no
 * existe.
 */
@Repository
public class JdbcRepositorioCalibracion implements RepositorioCalibracion {

    private final JdbcClient jdbc;

    public JdbcRepositorioCalibracion(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Calibracion vigente() {
        return jdbc.sql("""
                SELECT peso_procesos, peso_memoria, peso_archivos,
                       veto_habilitado, umbral_veto_componente
                FROM monitor_calibracion
                WHERE vigente_hasta IS NULL
                ORDER BY vigente_desde DESC
                LIMIT 1
                """)
            .query(this::mapear)
            .optional()
            .orElse(null);
    }

    @Override
    public void registrar(Calibracion nueva) {
        OffsetDateTime ahora = OffsetDateTime.now();

        jdbc.sql("UPDATE monitor_calibracion SET vigente_hasta = :ahora WHERE vigente_hasta IS NULL")
            .param("ahora", ahora)
            .update();

        jdbc.sql("""
                INSERT INTO monitor_calibracion
                    (nombre, vigente_desde, peso_procesos, peso_memoria, peso_archivos,
                     veto_habilitado, umbral_veto_componente)
                VALUES (:nombre, :vigente_desde, :peso_procesos, :peso_memoria, :peso_archivos,
                        :veto_habilitado, :umbral_veto_componente)
                """)
            .param("nombre", "recalibracion-" + ahora)
            .param("vigente_desde", ahora)
            .param("peso_procesos", nueva.pesos().get(Componente.PROCESOS))
            .param("peso_memoria", nueva.pesos().get(Componente.MEMORIA))
            .param("peso_archivos", nueva.pesos().get(Componente.ARCHIVOS))
            .param("veto_habilitado", nueva.vetoHabilitado())
            .param("umbral_veto_componente", nueva.umbralVetoComponente())
            .update();
    }

    private Calibracion mapear(ResultSet rs, int rowNum) throws SQLException {
        Map<Componente, Double> pesos = Map.of(
            Componente.PROCESOS, rs.getDouble("peso_procesos"),
            Componente.MEMORIA, rs.getDouble("peso_memoria"),
            Componente.ARCHIVOS, rs.getDouble("peso_archivos"));
        return new Calibracion(pesos, rs.getBoolean("veto_habilitado"), rs.getDouble("umbral_veto_componente"));
    }
}
