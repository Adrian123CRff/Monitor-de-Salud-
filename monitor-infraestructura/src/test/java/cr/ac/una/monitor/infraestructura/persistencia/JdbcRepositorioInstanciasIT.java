package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.Instancia;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 *
 * A diferencia de los demás *IT de esta carpeta, esta clase SÍ inserta una
 * fila con activa = true (para probar el camino "aparece en la vista
 * general") -- por eso, a diferencia de ellos, también la borra en @AfterAll:
 * dejarla viva contaminaría GET /api/v1/instancias del monitor-api real que
 * comparte esta misma base Postgres (mismo patrón de contaminación ya
 * encontrado y arreglado para monitor_calibracion y monitor_alertas esta
 * sesión).
 */
class JdbcRepositorioInstanciasIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";
    private static final String ALIAS_ACTIVA = "IT-test-instancias-activa";
    private static final String ALIAS_INACTIVA = "IT-test-instancias-inactiva";

    private static HikariDataSource dataSource;

    @BeforeAll
    static void migrarYSembrar() throws Exception {
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
            limpiarPropias(st);
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo, activa)
                VALUES ('%s', 'localhost', 1521, 'FREE', 'CDB', true)
                """.formatted(ALIAS_ACTIVA));
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo, activa)
                VALUES ('%s', 'localhost', 1521, 'FREE', 'CDB', false)
                """.formatted(ALIAS_INACTIVA));
        }
    }

    @AfterAll
    static void limpiarYCerrar() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            limpiarPropias(st);
        }
        dataSource.close();
    }

    private static void limpiarPropias(Statement st) throws Exception {
        st.execute("DELETE FROM monitor_instancia WHERE alias IN ('%s', '%s')"
            .formatted(ALIAS_ACTIVA, ALIAS_INACTIVA));
    }

    @Test
    void listarActivas_incluye_las_activas_y_excluye_las_inactivas() {
        JdbcRepositorioInstancias repositorio = new JdbcRepositorioInstancias(dataSource);

        List<Instancia> activas = repositorio.listarActivas();

        assertThat(activas).anyMatch(i -> i.alias().equals(ALIAS_ACTIVA));
        assertThat(activas).noneMatch(i -> i.alias().equals(ALIAS_INACTIVA));
    }
}
