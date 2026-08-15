package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 * Aplica la migración V4 real con Flyway (columnas de veto + semilla de la
 * calibración inicial de ADR 0003).
 *
 * Orden explícito: el segundo test depende de que la semilla de V4 siga
 * siendo la vigente cuando arranca (JUnit 5 no garantiza el orden por
 * defecto entre métodos de una misma clase).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcRepositorioCalibracionIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";

    private static HikariDataSource dataSource;

    @BeforeAll
    static void migrar() {
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
    }

    @AfterAll
    static void cerrarDataSource() {
        dataSource.close();
    }

    @Test
    @Order(1)
    void vigente_devuelve_la_calibracion_inicial_sembrada_por_v4() {
        JdbcRepositorioCalibracion repositorio = new JdbcRepositorioCalibracion(dataSource);

        Calibracion vigente = repositorio.vigente();

        assertThat(vigente).isNotNull();
        assertThat(vigente.pesos()).containsEntry(Componente.PROCESOS, 0.30);
        assertThat(vigente.pesos()).containsEntry(Componente.MEMORIA, 0.35);
        assertThat(vigente.pesos()).containsEntry(Componente.ARCHIVOS, 0.35);
        assertThat(vigente.vetoHabilitado()).isTrue();
        assertThat(vigente.umbralVetoComponente()).isCloseTo(40.0, offset(0.01));
    }

    @Test
    @Order(2)
    void registrar_cierra_la_vigente_anterior_y_la_nueva_pasa_a_ser_vigente() {
        JdbcRepositorioCalibracion repositorio = new JdbcRepositorioCalibracion(dataSource);
        Calibracion nueva = new Calibracion(
            Map.of(Componente.PROCESOS, 0.25, Componente.MEMORIA, 0.25, Componente.ARCHIVOS, 0.50),
            false, 35.0);

        repositorio.registrar(nueva);
        Calibracion vigente = repositorio.vigente();

        assertThat(vigente.pesos()).containsEntry(Componente.ARCHIVOS, 0.50);
        assertThat(vigente.vetoHabilitado()).isFalse();
        assertThat(vigente.umbralVetoComponente()).isCloseTo(35.0, offset(0.01));
    }
}
