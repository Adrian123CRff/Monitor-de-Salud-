package cr.ac.una.monitor.infraestructura.oracle;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecoleccionFallidaException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
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
 * Lee p1..p8 de V$RESOURCE_LIMIT / V$SESSION / V$SESSION_LONGOPS (SQL de la
 * skill oracle-vistas-dinamicas/references/sql-procesos.md, verificado
 * contra una instancia Oracle 23ai Free real) y calcula las variables
 * derivadas que consume CalculadorComponente: util_procesos_pct y
 * util_sesiones_pct (catalogo-variables.md "Variables derivadas").
 */
@Component
public class JdbcRecolectorProcesos implements RecolectorProcesos {

    private static final String SQL = """
        SELECT
            lim.procesos_actuales      AS p1,
            lim.procesos_maximos       AS p2,
            lim.sesiones_actuales      AS p3,
            ses.activas                AS p4,
            ses.inactivas              AS p5,
            ses.bloqueadas             AS p6,
            lop.operaciones_largas     AS p7,
            lim.peor_utilizacion_pct   AS p8,
            lim.limite_procesos,
            lim.limite_sesiones,
            ses.bloqueo_max_seg
        FROM
            ( SELECT
                MAX(CASE WHEN resource_name = 'processes' THEN current_utilization END) AS procesos_actuales,
                MAX(CASE WHEN resource_name = 'processes' THEN max_utilization     END) AS procesos_maximos,
                MAX(CASE WHEN resource_name = 'sessions'  THEN current_utilization END) AS sesiones_actuales,
                MAX(CASE WHEN resource_name = 'processes'
                         THEN CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1') THEN NULL
                                   ELSE TO_NUMBER(TRIM(limit_value)) END END)           AS limite_procesos,
                MAX(CASE WHEN resource_name = 'sessions'
                         THEN CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1') THEN NULL
                                   ELSE TO_NUMBER(TRIM(limit_value)) END END)           AS limite_sesiones,
                ROUND(MAX(CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1')
                                    OR TO_NUMBER(TRIM(limit_value)) = 0 THEN 0
                               ELSE current_utilization * 100
                                    / TO_NUMBER(TRIM(limit_value)) END), 2)             AS peor_utilizacion_pct
              FROM v$resource_limit
              WHERE resource_name IN ('processes','sessions','transactions',
                                      'enqueue_locks','enqueue_resources')
            ) lim,
            ( SELECT
                COUNT(CASE WHEN status = 'ACTIVE'   THEN 1 END)              AS activas,
                COUNT(CASE WHEN status = 'INACTIVE' THEN 1 END)              AS inactivas,
                COUNT(CASE WHEN blocking_session IS NOT NULL THEN 1 END)     AS bloqueadas,
                NVL(MAX(CASE WHEN blocking_session IS NOT NULL
                             THEN seconds_in_wait END), 0)                   AS bloqueo_max_seg
              FROM v$session
              WHERE type = 'USER'
            ) ses,
            ( SELECT COUNT(*) AS operaciones_largas
              FROM v$session_longops
              WHERE time_remaining > 0
            ) lop
        """;

    private final JdbcClient jdbc;

    public JdbcRecolectorProcesos(@Qualifier("oracleMonitoreado") DataSource ds) {
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
        double p1 = rs.getDouble("p1");
        double p3 = rs.getDouble("p3");
        Double limiteProcesos = valorONulo(rs, "limite_procesos");
        Double limiteSesiones = valorONulo(rs, "limite_sesiones");

        valores.put("p1_procesos_actuales", p1);
        valores.put("p2_procesos_maximos", rs.getDouble("p2"));
        valores.put("p3_sesiones_actuales", p3);
        valores.put("p4_sesiones_activas", rs.getDouble("p4"));
        valores.put("p5_sesiones_inactivas", rs.getDouble("p5"));
        valores.put("p6_sesiones_bloqueadas", rs.getDouble("p6"));
        valores.put("p7_operaciones_largas", rs.getDouble("p7"));
        valores.put("p8_peor_util_recurso", rs.getDouble("p8"));
        valores.put("bloqueo_max_seg", rs.getDouble("bloqueo_max_seg"));

        // UNLIMITED (limite null) => sin restricción, utilización 0, no error de división.
        valores.put("util_procesos_pct",
            limiteProcesos != null && limiteProcesos > 0 ? p1 / limiteProcesos * 100 : 0.0);
        valores.put("util_sesiones_pct",
            limiteSesiones != null && limiteSesiones > 0 ? p3 / limiteSesiones * 100 : 0.0);

        return new Muestra(Componente.PROCESOS, Instant.now(), valores, false);
    }

    private Double valorONulo(ResultSet rs, String columna) throws SQLException {
        double v = rs.getDouble(columna);
        return rs.wasNull() ? null : v;
    }
}
