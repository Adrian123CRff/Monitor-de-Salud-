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
 * b2/b3/b4 ya NO usan AVERAGE_WAIT/TOTAL_WAITS directamente -- esos son
 * acumulados desde el arranque de la instancia, el mismo problema que
 * m9_cache_hit_pct en memoria (ver JdbcRecolectorMemoria). Este
 * recolector trae TIME_WAITED y TOTAL_WAITS crudos (contadores reales,
 * verificado contra Oracle: TIME_WAITED/TOTAL_WAITS ≈ AVERAGE_WAIT, así
 * que la relación es consistente); MuestrearInstanciaServicio calcula la
 * delta contra la última muestra guardada, igual que con memoria.
 *
 * Solo cubre un DBWR (DBW0); instancias con múltiples DBWR (DBW1, DBW2...)
 * quedan fuera de esta V1.
 */
@Component
public class JdbcRecolectorProcesosFondo implements RecolectorProcesosFondo {

    private static final String SQL = """
        SELECT
            bg.procesos_caidos AS b1,
            NVL(ev.lgwr_time_waited, 0) AS lgwr_tw,
            NVL(ev.lgwr_total_waits, 0) AS lgwr_n,
            NVL(ev.dbwr_time_waited, 0) AS dbwr_tw,
            NVL(ev.dbwr_total_waits, 0) AS dbwr_n,
            NVL(ev.ckpt_switch_incompleto, 0) AS ckpt_n
        FROM
            ( SELECT COUNT(CASE WHEN paddr = '00' THEN 1 END) AS procesos_caidos
              FROM v$bgprocess
              WHERE name IN ('DBW0','LGWR','CKPT','PMON','SMON')
            ) bg,
            ( SELECT
                MAX(CASE WHEN event = 'log file sync' THEN time_waited END) AS lgwr_time_waited,
                MAX(CASE WHEN event = 'log file sync' THEN total_waits  END) AS lgwr_total_waits,
                MAX(CASE WHEN event = 'db file async I/O submit' THEN time_waited END) AS dbwr_time_waited,
                MAX(CASE WHEN event = 'db file async I/O submit' THEN total_waits  END) AS dbwr_total_waits,
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
        valores.put("b2_lgwr_time_waited_acum", rs.getDouble("lgwr_tw"));
        valores.put("b2_lgwr_total_waits_acum", rs.getDouble("lgwr_n"));
        valores.put("b3_dbwr_time_waited_acum", rs.getDouble("dbwr_tw"));
        valores.put("b3_dbwr_total_waits_acum", rs.getDouble("dbwr_n"));
        valores.put("b4_ckpt_switch_incompleto_acum", rs.getDouble("ckpt_n"));

        return new Muestra(Componente.PROCESOS, Instant.now(), valores, false);
    }
}
