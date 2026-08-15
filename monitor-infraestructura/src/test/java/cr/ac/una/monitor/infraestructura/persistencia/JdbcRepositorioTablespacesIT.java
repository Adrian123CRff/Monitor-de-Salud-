package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/** Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero. */
class JdbcRepositorioTablespacesIT {

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
    void guarda_una_fila_por_tablespace_en_el_mismo_ciclo() throws Exception {
        JdbcRepositorioTablespaces repositorio = new JdbcRepositorioTablespaces(dataSource);
        Instant momento = Instant.now();
        List<DetalleTablespace> detalle = List.of(
            new DetalleTablespace("SYSTEM", 12.34, 4096.0, 1000000.0),
            new DetalleTablespace("USERS", 0.5, 512.0, 500000.0));

        repositorio.guardar(INSTANCIA, momento, detalle);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT tablespace_name, used_percent, used_bytes, max_bytes
                 FROM monitor_tablespace
                 WHERE instancia_id = ? AND muestreado_en = ?
                 ORDER BY tablespace_name
                 """)) {
            ps.setLong(1, INSTANCIA.valor());
            ps.setObject(2, momento.atOffset(java.time.ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("tablespace_name")).isEqualTo("SYSTEM");
                assertThat(rs.getDouble("used_percent")).isCloseTo(12.34, offset(0.01));
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("tablespace_name")).isEqualTo("USERS");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    void ultimo_devuelve_solo_las_filas_del_ciclo_mas_reciente() {
        JdbcRepositorioTablespaces repositorio = new JdbcRepositorioTablespaces(dataSource);
        // Ciclo viejo, no debería aparecer en ultimo().
        repositorio.guardar(INSTANCIA, Instant.now().minusSeconds(120),
            List.of(new DetalleTablespace("VIEJO", 1.0, 1.0, 1.0)));
        List<DetalleTablespace> detalleReciente = List.of(
            new DetalleTablespace("SYSTEM", 40.0, 400.0, 1000.0),
            new DetalleTablespace("USERS", 10.0, 100.0, 1000.0));
        repositorio.guardar(INSTANCIA, Instant.now(), detalleReciente);

        List<DetalleTablespace> ultimo = repositorio.ultimo(INSTANCIA);

        assertThat(ultimo).extracting(DetalleTablespace::nombre).containsExactlyInAnyOrder("SYSTEM", "USERS");
        assertThat(ultimo).noneMatch(ts -> ts.nombre().equals("VIEJO"));
    }
}
