package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.alertas.Nivel;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 * Orden explícito: cada test depende del estado que deja el anterior
 * (abrir -> buscar -> cerrar), igual que el ciclo de vida real.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcRepositorioAlertasIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";
    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

    private static HikariDataSource dataSource;
    private static JdbcRepositorioAlertas repositorio;

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
        repositorio = new JdbcRepositorioAlertas(dataSource);

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo)
                VALUES ('IT-test', 'localhost', 1521, 'FREE', 'CDB')
                ON CONFLICT (alias) DO NOTHING
                """);
            limpiarAlertasDePrueba(st);
        }
    }

    /**
     * Encontrado por auditoría externa (ver docs/, mismo patrón que
     * JdbcRepositorioCalibracionIT): INSTANCIA = InstanciaId(1L) es la MISMA
     * instancia que usa el monitor-api real corriendo en Docker (Postgres
     * compartido) -- @Order(6)/(7) dejan "a2_datafiles_offline_it" y
     * "orden_test_it" abiertas a propósito para probar el orden por severidad,
     * y sin este @AfterAll esas dos alertas de prueba aparecían en
     * GET /api/v1/instancias/1/alertas del sistema real hasta la siguiente
     * corrida de este test.
     */
    @AfterAll
    static void limpiarYCerrarDataSource() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            limpiarAlertasDePrueba(st);
        }
        dataSource.close();
    }

    private static void limpiarAlertasDePrueba(Statement st) throws Exception {
        // Sin esto, re-ejecutar esta clase deja alertas "_it" abiertas de la
        // corrida anterior -- la siguiente corrida encontraría DOS filas
        // abiertas para la misma (instancia, variable, entidad) y buscarAbierta()
        // (que espera una sola) reventaría con IncorrectResultSizeDataAccessException.
        st.execute("""
            DELETE FROM monitor_alertas
            WHERE variable IN ('peor_tablespace_pct_it', 'a2_datafiles_offline_it', 'orden_test_it')
            """);
    }

    @Test
    @Order(1)
    void no_hay_alerta_abierta_todavia() {
        Optional<Alerta> abierta = repositorio.buscarAbierta(INSTANCIA, "peor_tablespace_pct_it", Optional.of("USERS"));

        assertThat(abierta).isEmpty();
    }

    @Test
    @Order(2)
    void abrir_persiste_y_asigna_id() {
        Alerta nueva = new Alerta(null, INSTANCIA, Componente.ARCHIVOS, "peor_tablespace_pct_it",
            Optional.of("USERS"), Nivel.ADVERTENCIA, 76.0, 75.0, "USERS al 76.0%.", Instant.now(), Optional.empty());

        Alerta guardada = repositorio.abrir(nueva);

        assertThat(guardada.id()).isNotNull();
    }

    @Test
    @Order(3)
    void buscarAbierta_encuentra_la_que_se_acaba_de_abrir_por_instancia_variable_y_entidad() {
        Optional<Alerta> abierta = repositorio.buscarAbierta(INSTANCIA, "peor_tablespace_pct_it", Optional.of("USERS"));

        assertThat(abierta).isPresent();
        assertThat(abierta.get().nivel()).isEqualTo(Nivel.ADVERTENCIA);
        assertThat(abierta.get().valor()).isCloseTo(76.0, offset(0.01));
        assertThat(abierta.get().abierta()).isTrue();
    }

    @Test
    @Order(4)
    void aparece_en_abiertas() {
        List<Alerta> abiertas = repositorio.abiertas(INSTANCIA);

        assertThat(abiertas).anyMatch(a -> a.variable().equals("peor_tablespace_pct_it")
            && a.entidad().equals(Optional.of("USERS")));
    }

    @Test
    @Order(5)
    void cerrar_hace_que_buscarAbierta_deje_de_encontrarla() {
        Alerta abierta = repositorio.buscarAbierta(INSTANCIA, "peor_tablespace_pct_it", Optional.of("USERS"))
            .orElseThrow();

        repositorio.cerrar(abierta, Instant.now());

        assertThat(repositorio.buscarAbierta(INSTANCIA, "peor_tablespace_pct_it", Optional.of("USERS"))).isEmpty();
    }

    @Test
    @Order(6)
    void una_alerta_sin_entidad_no_choca_con_una_que_si_la_tiene() {
        Alerta sinEntidad = new Alerta(null, INSTANCIA, Componente.ARCHIVOS, "a2_datafiles_offline_it",
            Optional.empty(), Nivel.CRITICO, 1.0, 1.0, "Datafiles offline: 1.", Instant.now(), Optional.empty());
        repositorio.abrir(sinEntidad);

        Optional<Alerta> encontrada = repositorio.buscarAbierta(INSTANCIA, "a2_datafiles_offline_it", Optional.empty());
        Optional<Alerta> conEntidadDistinta =
            repositorio.buscarAbierta(INSTANCIA, "a2_datafiles_offline_it", Optional.of("USERS"));

        assertThat(encontrada).isPresent();
        assertThat(conEntidadDistinta).isEmpty();
    }

    @Test
    @Order(7)
    void abiertas_ordena_por_severidad_no_alfabeticamente_por_el_nombre_del_nivel() {
        // a2_datafiles_offline_it (CRITICO) sigue abierta desde @Order(6) --
        // si se ordenara mal (p. ej. alfabético ADVERTENCIA/ALTO/CRITICO ascendente)
        // esta nueva ADVERTENCIA quedaría antes de la CRITICO en vez de después.
        Alerta advertencia = new Alerta(null, INSTANCIA, Componente.ARCHIVOS, "orden_test_it",
            Optional.empty(), Nivel.ADVERTENCIA, 76.0, 75.0, "x", Instant.now(), Optional.empty());
        repositorio.abrir(advertencia);

        List<Alerta> abiertas = repositorio.abiertas(INSTANCIA);
        int indiceCritico = indiceDe(abiertas, "a2_datafiles_offline_it");
        int indiceAdvertencia = indiceDe(abiertas, "orden_test_it");

        assertThat(indiceCritico).isLessThan(indiceAdvertencia);
    }

    private static int indiceDe(List<Alerta> abiertas, String variable) {
        for (int i = 0; i < abiertas.size(); i++) {
            if (abiertas.get(i).variable().equals(variable)) {
                return i;
            }
        }
        throw new AssertionError("No se encontró " + variable + " entre las abiertas");
    }
}
