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
 * Lee m1..m9 de V$SGAINFO / V$SGASTAT / V$PGASTAT (SQL de la skill
 * oracle-vistas-dinamicas/references/sql-memoria.md).
 *
 * PENDIENTE (deliberado): NO calcula "over_alloc_delta" -- eso requiere
 * comparar m8 con la última muestra guardada (y detectar reinicio via
 * startup_time), lo cual necesita RepositorioMuestras.ultima(...), que
 * este recolector no tiene ni debería tener (su responsabilidad es leer
 * el estado crudo actual, no la orquestación histórica). Esa comparación
 * pertenece a MuestrearInstanciaServicio o a un servicio de dominio nuevo.
 * Mientras tanto, CalculadorComponente simplemente ignora la variable
 * ausente y reparte el peso entre las que sí llegan (pga_uso_pct,
 * cache_hit_pct_delta) -- señal reducida, no un crash.
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
            pga.pga_target          AS pga_target_bytes
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
            ) pga
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
        double cacheHitAcumulado = rs.getDouble("m9"); // acumulado; ver nota de clase arriba

        valores.put("m1_sga_total_bytes", rs.getDouble("m1"));
        valores.put("m2_sga_libre_bytes", rs.getDouble("m2"));
        valores.put("m3_shared_pool_bytes", rs.getDouble("m3"));
        valores.put("m4_buffer_cache_bytes", rs.getDouble("m4"));
        valores.put("m5_pga_asignada_bytes", pgaAsignada);
        valores.put("m6_pga_en_uso_bytes", rs.getDouble("m6"));
        valores.put("m7_pga_maxima_bytes", rs.getDouble("m7"));
        valores.put("m8_over_alloc_acum", rs.getDouble("m8"));
        valores.put("m9_cache_hit_pct", cacheHitAcumulado); // nombre de columna en el esquema; ver nota arriba: es acumulado
        if (pgaTarget != null) {
            valores.put("pga_target_bytes", pgaTarget);
        }

        // pga_uso_pct SÍ es instantáneo (no acumulado) -- puede pasar de 100, no se recorta (ver skill).
        if (pgaTarget != null && pgaTarget > 0) {
            valores.put("pga_uso_pct", pgaAsignada / pgaTarget * 100);
        }
        // cache_hit_pct_delta: hasta que exista el cálculo de delta (ver nota de la
        // clase), usamos el acumulado tal cual -- es una aproximación deliberadamente
        // imprecisa en instancias con uptime largo (ver skill), documentada, no oculta.
        valores.put("cache_hit_pct_delta", cacheHitAcumulado);

        return new Muestra(Componente.MEMORIA, Instant.now(), valores, false);
    }

    private Double valorONulo(ResultSet rs, String columna) throws SQLException {
        double v = rs.getDouble(columna);
        return rs.wasNull() ? null : v;
    }
}
