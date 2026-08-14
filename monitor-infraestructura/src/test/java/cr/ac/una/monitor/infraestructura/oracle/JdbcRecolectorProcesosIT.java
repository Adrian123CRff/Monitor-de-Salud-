package cr.ac.una.monitor.infraestructura.oracle;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración manual contra el docker-compose local (Oracle Free
 * real en localhost:1521). NO corre con `mvnw test` (sufijo IT, sin
 * failsafe configurado a propósito): requiere `docker compose up -d`
 * primero. Ejecutar explícitamente con:
 *   mvnw -pl monitor-infraestructura test -Dtest=JdbcRecolectorProcesosIT
 *
 * Sirve como plantilla para migrar a Testcontainers (paso 3 del orden de
 * construcción sugerido) cuando el equipo quiera que esto corra en CI.
 */
class JdbcRecolectorProcesosIT {

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
    void recolecta_procesos_reales_de_la_instancia_docker() {
        JdbcRecolectorProcesos recolector = new JdbcRecolectorProcesos(dataSource);

        Muestra muestra = recolector.recolectar(new InstanciaId(1L));

        assertThat(muestra.valores()).containsKeys(
            "p1_procesos_actuales", "p6_sesiones_bloqueadas",
            "util_procesos_pct", "util_sesiones_pct", "bloqueo_max_seg");
        assertThat(muestra.valores().get("p1_procesos_actuales")).isGreaterThan(0);
        assertThat(muestra.valores().get("util_procesos_pct")).isBetween(0.0, 100.0);
        assertThat(muestra.valores().get("util_sesiones_pct")).isBetween(0.0, 100.0);
    }
}
