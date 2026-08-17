package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Tres DataSource, deliberadamente distintos.
 *
 * Dos hacia la MISMA instancia Oracle observada (ambos solo lectura, nunca
 * migrados) porque Oracle Free es multitenant (CDB + PDB, ver skill
 * oracle-vistas-dinamicas/references/cdb-pdb.md) y ninguna de las dos
 * conexiones ve todo lo que este proyecto necesita:
 *
 * - "oracleMonitoreado" (CDB$ROOT, servicio FREE): V$SESSION, V$RESOURCE_LIMIT,
 *   V$SGASTAT, V$PGASTAT, V$BGPROCESS, V$SYSTEM_EVENT — SGA/PGA/límites de
 *   recursos describen la instancia completa y solo se pueblan correctamente
 *   consultados desde la raíz. Verificado en vivo: "aggregate PGA target
 *   parameter" da 536870912 desde FREE y 0 desde FREEPDB1 — no es que falle,
 *   devuelve datos válidos pero vacíos, silenciosamente. Usan esta conexión
 *   JdbcRecolectorProcesos, JdbcRecolectorMemoria y JdbcRecolectorProcesosFondo.
 * - "oracleMonitoreadoPdb" (FREEPDB1): DBA_TABLESPACE_USAGE_METRICS, V$DATAFILE,
 *   V$TEMPFILE — estas SÍ son por contenedor: conectado a la raíz, un
 *   despliegue con datos de aplicación reales en el PDB queda ciego a sus
 *   propios tablespaces (mismo síntoma: cero filas de error, solo las del
 *   contenedor equivocado). V$LOG/V$LOGFILE resultaron idénticas en ambos
 *   contextos en las pruebas (redo es de instancia, no por PDB), así que no
 *   hay conflicto en tenerlas en esta conexión también. Usa esta conexión
 *   solo JdbcRecolectorArchivos.
 *
 * Y el histórico Postgres (ADR 0002, es el @Primary — así Flyway migra el
 * histórico, no la instancia observada, sin configuración adicional).
 */
@Configuration
public class DataSourceConfig {

    @Bean("oracleMonitoreado")
    @ConfigurationProperties("monitor.datasource.monitoreada")
    public DataSource oracleMonitoreado() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(3);        // pequeño: el monitor no debe pesar sobre la instancia
        ds.setReadOnly(true);            // nunca escribe en la base observada
        ds.setPoolName("monitor-lectura");
        // Etiqueta las sesiones del monitor en V$SESSION.PROGRAM para poder
        // excluirlas de p1/p3 (decisión del registro: no contar el propio poller).
        ds.addDataSourceProperty("v$session.program", "monitor-salud-oracle");
        // Timeout de socket a nivel del driver, defensa en profundidad además
        // de JdbcTemplate.setQueryTimeout (JdbcClienteConTimeout, monitor-infraestructura.oracle):
        // ese cancela la sentencia con un OCI cancel "amable"; este es la red
        // dándose por vencida si ni siquiera eso responde (p. ej. el host de
        // Oracle swapeando o una partición de red real).
        ds.addDataSourceProperty("oracle.jdbc.ReadTimeout", "15000");
        return ds;
    }

    @Bean("oracleMonitoreadoPdb")
    @ConfigurationProperties("monitor.datasource.monitoreada-pdb")
    public DataSource oracleMonitoreadoPdb() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(2);
        ds.setReadOnly(true);
        ds.setPoolName("monitor-lectura-pdb");
        ds.addDataSourceProperty("oracle.jdbc.ReadTimeout", "15000");
        return ds;
    }

    @Bean("historico")
    @Primary
    @ConfigurationProperties("monitor.datasource.historico")
    public DataSource historico() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
