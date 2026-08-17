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

import java.sql.Connection;
import java.sql.Statement;
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
 *
 * Higiene entre corridas (mismo problema que ya se arregló en
 * JdbcRepositorioAlertasIT): registrar_cierra_la_vigente_anterior... deja
 * una fila "recalibracion-..." vigente al terminar, y sin limpiarla la
 * siguiente corrida (o, más grave, el monitor-api real que comparte esta
 * misma base histórico) hereda esa calibración de prueba -- se encontró
 * en vivo con el veto deshabilitado y pesos 0.25/0.25/0.50 en vez de los
 * 0.30/0.35/0.35 de ADR 0003. Restaura la semilla de V4 como vigente tanto
 * en @BeforeAll (para que este test no dependa de lo que dejó la corrida
 * anterior) como en @AfterAll (para que el monitor-api real, que comparte
 * esta misma base histórico, no quede con la calibración de prueba después
 * de correr esto). Sin borrar nada: monitor_indices/monitor_umbral pueden
 * referenciar las filas viejas por FK.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcRepositorioCalibracionIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";

    private static HikariDataSource dataSource;

    @BeforeAll
    static void migrar() throws Exception {
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

        restaurarSemillaV4Vigente();
    }

    @AfterAll
    static void restaurarYCerrar() throws Exception {
        restaurarSemillaV4Vigente();
        dataSource.close();
    }

    private static void restaurarSemillaV4Vigente() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE monitor_calibracion SET vigente_hasta = now() "
                + "WHERE nombre != 'inicial-adr-0003' AND vigente_hasta IS NULL");
            st.execute("UPDATE monitor_calibracion SET vigente_hasta = NULL "
                + "WHERE nombre = 'inicial-adr-0003'");
        }
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
