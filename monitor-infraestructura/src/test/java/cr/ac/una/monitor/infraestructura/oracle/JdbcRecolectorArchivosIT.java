package cr.ac.una.monitor.infraestructura.oracle;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ver JdbcRecolectorProcesosIT: requiere `docker compose up -d` primero. */
class JdbcRecolectorArchivosIT {

    private static HikariDataSource dataSource;

    @BeforeAll
    static void crearDataSource() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        dataSource.setUsername("c##monitor");
        dataSource.setPassword("MonitorPass123");
        dataSource.setMaximumPoolSize(2);
        dataSource.setReadOnly(true);
    }

    @AfterAll
    static void cerrarDataSource() {
        dataSource.close();
    }

    @Test
    void recolecta_archivos_reales_de_la_instancia_docker() {
        JdbcRecolectorArchivos recolector = new JdbcRecolectorArchivos(dataSource);

        Muestra muestra = recolector.recolectar(new InstanciaId(1L));

        assertThat(muestra.valores()).containsKeys(
            "a1_datafiles_online", "peor_tablespace_pct", "redundancia_redo", "a7_archivos_invalidos");
        assertThat(muestra.valores().get("a1_datafiles_online")).isGreaterThan(0);
        assertThat(muestra.valores().get("peor_tablespace_pct")).isBetween(0.0, 100.0);
    }
}
