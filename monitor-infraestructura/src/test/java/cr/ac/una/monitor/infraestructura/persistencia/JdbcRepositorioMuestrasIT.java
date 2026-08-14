package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Ver JdbcRecolectorProcesosIT: requiere `docker compose up -d` primero.
 * Aplica la migración real con Flyway (no psql a mano) para dejar
 * flyway_schema_history consistente, igual que hará la app en su primer
 * arranque real.
 */
class JdbcRepositorioMuestrasIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";
    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

    private static HikariDataSource dataSource;

    @BeforeAll
    static void migrarYPrepararInstancia() throws Exception {
        Flyway.configure()
            .dataSource(URL, USER, PASSWORD)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(URL);
        dataSource.setUsername(USER);
        dataSource.setPassword(PASSWORD);
        dataSource.setMaximumPoolSize(2);

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo)
                VALUES ('IT-test', 'localhost', 1521, 'FREE', 'CDB')
                ON CONFLICT (alias) DO NOTHING
                """);
        }
    }

    @AfterAll
    static void cerrarDataSource() {
        dataSource.close();
    }

    @Test
    void guarda_y_recupera_la_ultima_muestra_de_procesos() {
        JdbcRepositorioMuestras repositorio = new JdbcRepositorioMuestras(dataSource);
        Muestra muestra = new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "p1_procesos_actuales", 84.0,
            "util_procesos_pct", 42.0,
            "p6_sesiones_bloqueadas", 0.0
        ), false);

        repositorio.guardar(INSTANCIA, muestra);
        Optional<Muestra> recuperada = repositorio.ultima(INSTANCIA, Componente.PROCESOS);

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().valores().get("p1_procesos_actuales")).isCloseTo(84.0, offset(0.01));
        assertThat(recuperada.get().valores().get("p6_sesiones_bloqueadas")).isCloseTo(0.0, offset(0.01));
        // util_procesos_pct es derivada, no tiene columna en el esquema (modelo-datos.md
        // decisión #2: solo se guardan crudos) -- correcto que no sobreviva el guardar/ultima.
        assertThat(recuperada.get().valores()).doesNotContainKey("util_procesos_pct");
    }
}
