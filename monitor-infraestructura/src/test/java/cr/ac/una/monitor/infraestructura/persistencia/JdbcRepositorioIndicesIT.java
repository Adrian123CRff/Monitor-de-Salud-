package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 * Aplica la migración V5 real con Flyway (ip/im/ia/calibracion_id nullable,
 * parcial/causas nuevas).
 */
class JdbcRepositorioIndicesIT {

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
    void guarda_y_recupera_un_isbd_completo() {
        JdbcRepositorioIndices repositorio = new JdbcRepositorioIndices(dataSource);
        Instant momento = Instant.now();
        Isbd isbd = new Isbd(momento, 82.35, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 74.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 91.0, Map.of())),
            false, List.of(), false);

        repositorio.guardar(INSTANCIA, isbd);
        Optional<Isbd> recuperado = repositorio.ultimo(INSTANCIA);

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().puntuacion()).isCloseTo(82.35, offset(0.01));
        assertThat(recuperado.get().estado()).isEqualTo(Estado.SALUDABLE);
        assertThat(recuperado.get().ip()).isPresent();
        assertThat(recuperado.get().ip().get().puntuacion()).isCloseTo(82.0, offset(0.01));
        assertThat(recuperado.get().im().get().puntuacion()).isCloseTo(74.0, offset(0.01));
        assertThat(recuperado.get().ia().get().puntuacion()).isCloseTo(91.0, offset(0.01));
        assertThat(recuperado.get().parcial()).isFalse();
        assertThat(recuperado.get().causas()).isEmpty();
    }

    @Test
    void guarda_y_recupera_un_isbd_parcial_con_causas_y_componente_ausente() {
        JdbcRepositorioIndices repositorio = new JdbcRepositorioIndices(dataSource);
        Instant momento = Instant.now();
        Isbd isbd = new Isbd(momento, 87.7, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 85.0, Map.of())),
            Optional.empty(),
            Optional.of(new Indicador(Componente.ARCHIVOS, 90.0, Map.of())),
            false, List.of("MEMORIA: fallo de recolección, excluido del cálculo"), true);

        repositorio.guardar(INSTANCIA, isbd);
        Optional<Isbd> recuperado = repositorio.ultimo(INSTANCIA);

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().im()).isEmpty();
        assertThat(recuperado.get().parcial()).isTrue();
        assertThat(recuperado.get().causas()).containsExactly("MEMORIA: fallo de recolección, excluido del cálculo");
    }
}
