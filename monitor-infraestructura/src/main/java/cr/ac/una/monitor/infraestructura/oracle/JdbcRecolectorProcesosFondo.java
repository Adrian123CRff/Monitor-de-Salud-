package cr.ac.una.monitor.infraestructura.oracle;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecoleccionFallidaException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee el estado de los procesos de fondo mandatorios de V$BGPROCESS /
 * V$SYSTEM_EVENT (ADR 0006). Verificado a mano contra una instancia
 * Oracle 23ai Free real: los nombres de proceso son DBW0 (no "DBWR"),
 * LGWR, CKPT, PMON, SMON.
 *
 * b2/b3 usan AVERAGE_WAIT de V$SYSTEM_EVENT, que Oracle reporta en
 * centésimas de segundo (no milisegundos) -- de ahí los umbrales
 * ok=1/critico=10 en UmbralesIniciales (10ms / 100ms).
 *
 * Solo cubre un DBWR (DBW0); instancias con múltiples DBWR (DBW1, DBW2...)
 * quedan fuera de esta V1.
 */
@Component
public class JdbcRecolectorProcesosFondo implements RecolectorProcesosFondo {

    private static final String SQL = """
        SELECT
            bg.procesos_caidos          AS b1,
            NVL(ev.lgwr_espera_avg, 0)  AS b2,
            NVL(ev.dbwr_espera_avg, 0)  AS b3,
            NVL(ev.ckpt_switch_incompleto, 0) AS b4
        FROM
            ( SELECT COUNT(CASE WHEN paddr = '00' THEN 1 END) AS procesos_caidos
              FROM v$bgprocess
              WHERE name IN ('DBW0','LGWR','CKPT','PMON','SMON')
            ) bg,
            ( SELECT
                MAX(CASE WHEN event = 'log file sync' THEN average_wait END)            AS lgwr_espera_avg,
                MAX(CASE WHEN event = 'db file async I/O submit' THEN average_wait END) AS dbwr_espera_avg,
                MAX(CASE WHEN event = 'log file switch (checkpoint incomplete)' THEN total_waits END)
                    AS ckpt_switch_incompleto
              FROM v$system_event
            ) ev
        """;

    private final JdbcClient jdbc;

    public JdbcRecolectorProcesosFondo(@Qualifier("oracleMonitoreado") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Muestra recolectar(InstanciaId instancia) {
        try {
            return jdbc.sql(SQL).query(this::mapear).single();
        } catch (DataAccessException e) {
            throw new RecoleccionFallidaException(Componente.PROCESOS, instancia, e);
        }
    }

    private Muestra mapear(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Double> valores = new HashMap<>();
        valores.put("b1_procesos_caidos", rs.getDouble("b1"));
        valores.put("b2_lgwr_espera_avg", rs.getDouble("b2"));
        valores.put("b3_dbwr_espera_avg", rs.getDouble("b3"));
        valores.put("b4_ckpt_switch_incompleto", rs.getDouble("b4"));

        return new Muestra(Componente.PROCESOS, Instant.now(), valores, false);
    }
}
