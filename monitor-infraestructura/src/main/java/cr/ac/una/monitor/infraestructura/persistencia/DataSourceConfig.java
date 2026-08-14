package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Dos DataSource, deliberadamente distintos: uno hacia la instancia Oracle
 * observada (solo lectura, nunca migrada), otro hacia el histórico Postgres
 * (ADR 0002, es el @Primary — así Flyway migra el histórico, no la instancia
 * observada, sin configuración adicional).
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
        return ds;
    }

    @Bean("historico")
    @Primary
    @ConfigurationProperties("monitor.datasource.historico")
    public DataSource historico() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
