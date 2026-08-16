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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
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

    // instancia_id=1 ('IT-test') también es, en este dev machine, el
    // instancia-id que usa el monitor-api real corriendo en Docker (ver
    // application.yml/V6__instancia_inicial.sql) -- coincidencia de IDs, no
    // por diseño, pero significa que monitor_procesos/monitor_memoria/
    // monitor_archivos para instancia_id=1 reciben filas nuevas cada ~60s
    // del planificador real mientras dockercompose esté arriba. enRango con
    // fechas fijas en el pasado es inmune (el planificador solo escribe
    // "ahora"), pero ultimasN() no lo es: "las últimas N" incluiría esas
    // filas reales. Por eso ultimasN_... usa una instancia propia, aislada.
    private static HikariDataSource dataSource;
    private static long instanciaAislada;

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
            st.execute("""
                INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo)
                VALUES ('IT-test-ultimasN', 'localhost', 1521, 'FREE', 'CDB')
                ON CONFLICT (alias) DO NOTHING
                """);
            try (ResultSet rs = st.executeQuery(
                    "SELECT id FROM monitor_instancia WHERE alias = 'IT-test-ultimasN'")) {
                rs.next();
                instanciaAislada = rs.getLong("id");
            }
            // Re-ejecutar esta clase de test más de una vez en el mismo dev DB
            // (pasó esta sesión) duplicaba las filas de fechas fijas de
            // enRango_... -- idempotencia explícita, igual que
            // JdbcRepositorioAlertasIT.
            st.execute("DELETE FROM monitor_archivos WHERE instancia_id = 1 "
                + "AND muestreado_en >= '2020-01-01T00:00:00Z' AND muestreado_en <= '2020-01-01T02:00:00Z'");
            st.execute("DELETE FROM monitor_procesos WHERE instancia_id = " + instanciaAislada);
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

    @Test
    void enRango_devuelve_solo_las_muestras_dentro_del_intervalo_ordenadas_por_tiempo() {
        JdbcRepositorioMuestras repositorio = new JdbcRepositorioMuestras(dataSource);
        // Fechas fijas en el pasado -- evita colisión con "Instant.now()" que insertan
        // otros tests de esta misma clase en la misma tabla/instancia.
        Instant t1 = Instant.parse("2020-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2020-01-01T01:00:00Z");
        Instant t3 = Instant.parse("2020-01-01T02:00:00Z");

        repositorio.guardar(INSTANCIA, new Muestra(Componente.ARCHIVOS, t1, Map.of("a1_datafiles_online", 1.0), false));
        repositorio.guardar(INSTANCIA, new Muestra(Componente.ARCHIVOS, t2, Map.of("a1_datafiles_online", 2.0), false));
        repositorio.guardar(INSTANCIA, new Muestra(Componente.ARCHIVOS, t3, Map.of("a1_datafiles_online", 3.0), false));

        List<Muestra> enRango = repositorio.enRango(INSTANCIA, Componente.ARCHIVOS, t1.plusSeconds(1), t3);

        assertThat(enRango).hasSize(2);
        assertThat(enRango.get(0).momento()).isEqualTo(t2);
        assertThat(enRango.get(0).valores().get("a1_datafiles_online")).isCloseTo(2.0, offset(0.01));
        assertThat(enRango.get(1).momento()).isEqualTo(t3);
        assertThat(enRango.get(1).valores().get("a1_datafiles_online")).isCloseTo(3.0, offset(0.01));
    }

    @Test
    void ultimasN_devuelve_como_maximo_n_filas_la_mas_reciente_primero() {
        JdbcRepositorioMuestras repositorio = new JdbcRepositorioMuestras(dataSource);
        // instancia aislada, no INSTANCIA (id=1) -- ver el comentario junto a
        // instanciaAislada: con id=1 estas fechas fijas competirían contra
        // las filas "ahora" que el planificador real sigue escribiendo.
        InstanciaId aislada = new InstanciaId(instanciaAislada);
        Instant t1 = Instant.parse("2021-06-01T00:00:00Z");
        Instant t2 = Instant.parse("2021-06-01T01:00:00Z");
        Instant t3 = Instant.parse("2021-06-01T02:00:00Z");

        repositorio.guardar(aislada, new Muestra(Componente.PROCESOS, t1, Map.of("p6_sesiones_bloqueadas", 1.0), false));
        repositorio.guardar(aislada, new Muestra(Componente.PROCESOS, t2, Map.of("p6_sesiones_bloqueadas", 2.0), false));
        repositorio.guardar(aislada, new Muestra(Componente.PROCESOS, t3, Map.of("p6_sesiones_bloqueadas", 3.0), false));

        List<Muestra> ultimas2 = repositorio.ultimasN(aislada, Componente.PROCESOS, 2);

        assertThat(ultimas2).hasSize(2);
        assertThat(ultimas2.get(0).momento()).isEqualTo(t3);
        assertThat(ultimas2.get(0).valores().get("p6_sesiones_bloqueadas")).isCloseTo(3.0, offset(0.01));
        assertThat(ultimas2.get(1).momento()).isEqualTo(t2);
        assertThat(ultimas2.get(1).valores().get("p6_sesiones_bloqueadas")).isCloseTo(2.0, offset(0.01));
    }
}
