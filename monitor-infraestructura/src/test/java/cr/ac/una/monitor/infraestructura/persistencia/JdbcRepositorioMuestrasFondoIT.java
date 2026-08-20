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
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 * Aplica la migración V3 real con Flyway (tabla monitor_procesos_fondo,
 * separada de monitor_procesos -- ver RepositorioMuestrasFondo).
 */
class JdbcRepositorioMuestrasFondoIT {

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
            // activa = false: ver JdbcRepositorioMuestrasIT para por qué.
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo, activa)
                VALUES ('IT-test', 'localhost', 1521, 'FREE', 'CDB', false)
                ON CONFLICT (alias) DO NOTHING
                """);
        }
    }

    @AfterAll
    static void cerrarDataSource() {
        dataSource.close();
    }

    @Test
    void guarda_y_recupera_la_ultima_muestra_de_procesos_de_fondo() {
        JdbcRepositorioMuestrasFondo repositorio = new JdbcRepositorioMuestrasFondo(dataSource);
        Muestra muestra = new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 0.0,
            "b2_lgwr_time_waited_acum", 40.0,
            "b2_lgwr_total_waits_acum", 95.0,
            "b2_lgwr_espera_avg", 0.42,
            "b4_ckpt_switch_incompleto_acum", 3.0
        ), false);

        repositorio.guardar(INSTANCIA, muestra);
        Optional<Muestra> recuperada = repositorio.ultima(INSTANCIA);

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().valores().get("b1_procesos_caidos")).isCloseTo(0.0, offset(0.01));
        assertThat(recuperada.get().valores().get("b2_lgwr_time_waited_acum")).isCloseTo(40.0, offset(0.01));
        assertThat(recuperada.get().valores().get("b2_lgwr_espera_avg")).isCloseTo(0.42, offset(0.001));
        assertThat(recuperada.get().instanciaReiniciada()).isFalse();
    }
}
