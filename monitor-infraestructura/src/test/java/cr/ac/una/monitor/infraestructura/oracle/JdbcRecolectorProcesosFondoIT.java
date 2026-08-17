package cr.ac.una.monitor.infraestructura.oracle;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Ver JdbcRecolectorProcesosIT: requiere `docker compose up -d` primero. */
class JdbcRecolectorProcesosFondoIT {

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
    void recolecta_procesos_de_fondo_reales_de_la_instancia_docker() {
        JdbcRecolectorProcesosFondo recolector = new JdbcRecolectorProcesosFondo(dataSource);

        // Este test, pasando, ya es en sí mismo la prueba positiva del chequeo
        // procesos_encontrados == 5 (ver JdbcRecolectorProcesosFondo.mapear()):
        // si el WHERE name IN (...) no hubiera encontrado los 5 procesos
        // mandatorios contra esta instancia real, recolectar() habría lanzado
        // RecoleccionFallidaException en vez de devolver la Muestra de abajo.
        Muestra muestra = recolector.recolectar(new InstanciaId(1L));

        assertThat(muestra.valores()).containsKeys(
            "b1_procesos_caidos",
            "b2_lgwr_time_waited_acum", "b2_lgwr_total_waits_acum",
            "b3_dbwr_time_waited_acum", "b3_dbwr_total_waits_acum",
            "b4_ckpt_switch_incompleto_acum");
        // DBW0, LGWR, CKPT, PMON y SMON deben estar activos en una instancia sana recién levantada.
        assertThat(muestra.valores().get("b1_procesos_caidos")).isEqualTo(0.0);
    }
}
