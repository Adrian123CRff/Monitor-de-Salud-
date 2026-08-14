package cr.ac.una.monitor.infraestructura.oracle;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ver JdbcRecolectorProcesosIT: requiere `docker compose up -d` primero. */
class JdbcRecolectorMemoriaIT {

    private static HikariDataSource dataSource;

    @BeforeAll
    static void crearDataSource() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:oracle:thin:@//localhost:1521/FREE");
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
    void recolecta_memoria_real_de_la_instancia_docker() {
        JdbcRecolectorMemoria recolector = new JdbcRecolectorMemoria(dataSource);

        Muestra muestra = recolector.recolectar(new InstanciaId(1L));

        assertThat(muestra.valores()).containsKeys(
            "m1_sga_total_bytes", "m5_pga_asignada_bytes", "m8_over_alloc_acum", "pga_uso_pct");
        assertThat(muestra.valores().get("m1_sga_total_bytes")).isGreaterThan(0);
        assertThat(muestra.valores().get("pga_uso_pct")).isGreaterThanOrEqualTo(0.0);
    }
}
