package cr.ac.una.monitor.infraestructura.oracle;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecoleccionFallidaException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
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
 * Lee m1..m9 de V$SGAINFO / V$SGASTAT / V$PGASTAT, más
 * V$SQL_WORKAREA_HISTOGRAM (SQL de la skill
 * oracle-vistas-dinamicas/references/sql-memoria.md).
 *
 * m9_cache_hit_pct se guarda como dato crudo/contexto pero NO se usa para
 * puntuar: es un promedio acumulado desde el arranque, no un contador, y no
 * se le puede sacar una delta real (ver UmbralesIniciales.memoria()).
 * m10_multipass_acum sí es un contador real -- verificado contra una
 * instancia Oracle viva antes de usarlo.
 *
 * Este recolector SOLO lee el estado crudo actual. Calcular las deltas
 * (m8_over_alloc_delta, m10_multipass_delta) requiere la última muestra
 * guardada y es responsabilidad de MuestrearInstanciaServicio, no de este
 * adaptador -- su trabajo es leer, no orquestar histórico.
 */
@Component
public class JdbcRecolectorMemoria implements RecolectorMemoria {

    private static final String SQL = """
        SELECT
            sga.sga_total_bytes     AS m1,
            sga.sga_libre_bytes     AS m2,
            pool.shared_pool_bytes  AS m3,
            sga.buffer_cache_bytes  AS m4,
            pga.pga_asignada        AS m5,
            pga.pga_en_uso          AS m6,
            pga.pga_maxima          AS m7,
            pga.over_alloc_acum     AS m8,
            pga.cache_hit_pct       AS m9,
            pga.pga_target          AS pga_target_bytes,
            wa.multipass_acum       AS m10
        FROM
            ( SELECT
                MAX(CASE WHEN name = 'Maximum SGA Size'          THEN bytes END) AS sga_total_bytes,
                MAX(CASE WHEN name = 'Free SGA Memory Available' THEN bytes END) AS sga_libre_bytes,
                MAX(CASE WHEN name = 'Buffer Cache Size'         THEN bytes END) AS buffer_cache_bytes
              FROM v$sgainfo
            ) sga,
            ( SELECT SUM(bytes) AS shared_pool_bytes
              FROM   v$sgastat
              WHERE  pool = 'shared pool'
            ) pool,
            ( SELECT
                MAX(CASE WHEN name = 'total PGA allocated'           THEN value END) AS pga_asignada,
                MAX(CASE WHEN name = 'total PGA inuse'               THEN value END) AS pga_en_uso,
                MAX(CASE WHEN name = 'maximum PGA allocated'         THEN value END) AS pga_maxima,
                MAX(CASE WHEN name = 'over allocation count'         THEN value END) AS over_alloc_acum,
                MAX(CASE WHEN name = 'cache hit percentage'          THEN value END) AS cache_hit_pct,
                MAX(CASE WHEN name = 'aggregate PGA target parameter' THEN value END) AS pga_target
              FROM v$pgastat
            ) pga,
            ( SELECT NVL(SUM(multipasses_executions), 0) AS multipass_acum
              FROM v$sql_workarea_histogram
            ) wa
        """;

    private final JdbcClient jdbc;

    public JdbcRecolectorMemoria(@Qualifier("oracleMonitoreado") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Muestra recolectar(InstanciaId instancia) {
        try {
            return jdbc.sql(SQL).query(this::mapear).single();
        } catch (DataAccessException e) {
            throw new RecoleccionFallidaException(Componente.MEMORIA, instancia, e);
        }
    }

    private Muestra mapear(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Double> valores = new HashMap<>();
        double pgaAsignada = rs.getDouble("m5");
        Double pgaTarget = valorONulo(rs, "pga_target_bytes");

        valores.put("m1_sga_total_bytes", rs.getDouble("m1"));
        valores.put("m2_sga_libre_bytes", rs.getDouble("m2"));
        valores.put("m3_shared_pool_bytes", rs.getDouble("m3"));
        valores.put("m4_buffer_cache_bytes", rs.getDouble("m4"));
        valores.put("m5_pga_asignada_bytes", pgaAsignada);
        valores.put("m6_pga_en_uso_bytes", rs.getDouble("m6"));
        valores.put("m7_pga_maxima_bytes", rs.getDouble("m7"));
        valores.put("m8_over_alloc_acum", rs.getDouble("m8"));
        valores.put("m9_cache_hit_pct", rs.getDouble("m9")); // crudo/contexto, no se puntúa
        valores.put("m10_multipass_acum", rs.getDouble("m10"));
        if (pgaTarget != null) {
            valores.put("pga_target_bytes", pgaTarget);
        }

        // pga_uso_pct SÍ es instantáneo (no acumulado) -- puede pasar de 100, no se recorta (ver skill).
        if (pgaTarget != null && pgaTarget > 0) {
            valores.put("pga_uso_pct", pgaAsignada / pgaTarget * 100);
        }

        return new Muestra(Componente.MEMORIA, Instant.now(), valores, false);
    }

    private Double valorONulo(ResultSet rs, String columna) throws SQLException {
        double v = rs.getDouble(columna);
        return rs.wasNull() ? null : v;
    }
}
