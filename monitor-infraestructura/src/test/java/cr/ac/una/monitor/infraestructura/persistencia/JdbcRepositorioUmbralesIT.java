package cr.ac.una.monitor.infraestructura.persistencia;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver JdbcRepositorioMuestrasIT: requiere `docker compose up -d` primero.
 * Aplica la migración V8 real con Flyway (monitor_umbral_puntuacion +
 * monitor_instancia.perfil).
 */
class JdbcRepositorioUmbralesIT {

    private static final String URL = "jdbc:postgresql://localhost:5432/monitor_historico";
    private static final String USER = "monitor";
    private static final String PASSWORD = "HistoricoPass123";
    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

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

    /**
     * Este IT comparte la base histórico con el monitor-api real (ver el
     * mismo patrón ya arreglado en JdbcRepositorioCalibracionIT y
     * JdbcRepositorioAlertasIT): dejar un perfil de prueba puesto en la
     * instancia 1 cambiaría los umbrales del sistema en vivo.
     */
    @AfterAll
    static void limpiarYCerrar() throws Exception {
        ejecutar("UPDATE monitor_instancia SET perfil = 'ESTANDAR' WHERE id = 1");
        ejecutar("DELETE FROM monitor_umbral_puntuacion WHERE perfil <> 'ESTANDAR'");
        dataSource.close();
    }

    private static void ejecutar(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * El test que de verdad importa: la semilla SQL de V8 y UmbralesIniciales
     * son dos copias de los mismos números, en lenguajes distintos. Nada
     * impide que alguien toque una y olvide la otra -- y como UmbralesIniciales
     * sigue siendo el respaldo en código, la divergencia sería silenciosa
     * (el monitor puntuaría distinto según si la tabla respondió o no).
     * Esto la convierte en un fallo de build.
     */
    @Test
    void la_semilla_de_v8_coincide_exactamente_con_umbrales_iniciales() {
        JdbcRepositorioUmbrales repositorio = new JdbcRepositorioUmbrales(dataSource);

        Map<GrupoUmbral, List<Umbral>> deLaTabla = repositorio.vigentes(INSTANCIA);
        Map<GrupoUmbral, List<Umbral>> delCodigo = UmbralesIniciales.porGrupo();

        assertThat(deLaTabla.keySet()).containsExactlyInAnyOrderElementsOf(delCodigo.keySet());
        for (GrupoUmbral grupo : delCodigo.keySet()) {
            assertThat(deLaTabla.get(grupo))
                .as("umbrales de %s", grupo)
                .containsExactlyInAnyOrderElementsOf(delCodigo.get(grupo));
        }
    }

    /**
     * El requisito "paramétrico" de las notas de clase: un perfil redefine
     * SOLO las variables que cambian con el tamaño y hereda el resto de
     * ESTANDAR. Sin esto habría que duplicar las 16 filas por cada perfil.
     */
    @Test
    void un_perfil_redefine_solo_su_variable_y_hereda_el_resto_de_estandar() throws Exception {
        try {
            // Una base "pequeña" tolera menos procesos antes de preocuparse.
            ejecutar("""
                INSERT INTO monitor_umbral_puntuacion
                    (perfil, grupo, variable, tipo, valor_ok, valor_critico,
                     puntos_por_evento, peso_en_componente, veto_absoluto, fuente)
                VALUES ('PEQUENA', 'PROCESOS_USUARIOS', 'util_procesos_pct',
                        'LINEAL_INVERTIDA', 50, 80, 0, 0.250, FALSE, 'Override de prueba (IT)')
                """);
            ejecutar("UPDATE monitor_instancia SET perfil = 'PEQUENA' WHERE id = 1");

            Map<GrupoUmbral, List<Umbral>> vigentes =
                new JdbcRepositorioUmbrales(dataSource).vigentes(INSTANCIA);
            List<Umbral> procesos = vigentes.get(GrupoUmbral.PROCESOS_USUARIOS);

            // La variable redefinida gana...
            assertThat(procesos).anySatisfy(u -> {
                assertThat(u.variable()).isEqualTo("util_procesos_pct");
                assertThat(u.valorOk()).isEqualTo(50.0);
                assertThat(u.valorCritico()).isEqualTo(80.0);
            });
            // ...y no aparece dos veces (el DISTINCT ON descarta la de ESTANDAR).
            assertThat(procesos).filteredOn(u -> u.variable().equals("util_procesos_pct")).hasSize(1);

            // El resto del grupo sigue viniendo de ESTANDAR, sin duplicados.
            assertThat(procesos).hasSize(UmbralesIniciales.procesosUsuarios().size());
            assertThat(procesos).anySatisfy(u -> {
                assertThat(u.variable()).isEqualTo("util_sesiones_pct");
                assertThat(u.valorOk()).isEqualTo(70.0);
            });

            // Y los grupos que ese perfil no toca quedan idénticos a ESTANDAR.
            assertThat(vigentes.get(GrupoUmbral.ARCHIVOS))
                .containsExactlyInAnyOrderElementsOf(UmbralesIniciales.archivos());
        } finally {
            ejecutar("UPDATE monitor_instancia SET perfil = 'ESTANDAR' WHERE id = 1");
            ejecutar("DELETE FROM monitor_umbral_puntuacion WHERE perfil = 'PEQUENA'");
        }
    }

    /**
     * Los tipos que no usan valor_ok/valor_critico los guardan en 0, y
     * veto_si_valor_supera es NULL salvo en peor_tablespace_pct -- comprobar
     * que el mapeo no confunde "0 en la base" con "NULL en la base"
     * (ResultSet.getDouble devuelve 0.0 para ambos; hace falta wasNull()).
     */
    @Test
    void el_veto_por_valor_y_el_veto_absoluto_sobreviven_al_viaje_de_ida_y_vuelta() {
        List<Umbral> archivos = new JdbcRepositorioUmbrales(dataSource)
            .vigentes(INSTANCIA).get(GrupoUmbral.ARCHIVOS);

        assertThat(archivos).anySatisfy(u -> {
            assertThat(u.variable()).isEqualTo("peor_tablespace_pct");
            assertThat(u.vetoSiValorSupera()).contains(98.0);
            assertThat(u.vetoAbsoluto()).isFalse();
        });
        assertThat(archivos).anySatisfy(u -> {
            assertThat(u.variable()).isEqualTo("a2_datafiles_offline");
            assertThat(u.tipo()).isEqualTo(TipoUmbral.CRITICO_SI_HAY_ALGUNO);
            assertThat(u.vetoAbsoluto()).isTrue();
            assertThat(u.vetoSiValorSupera()).isEmpty();
        });
    }

    /**
     * Una instancia que no existe no revienta: devuelve vacío y
     * MuestrearInstanciaServicio cae al respaldo en código.
     */
    @Test
    void una_instancia_inexistente_devuelve_un_mapa_vacio_sin_reventar() {
        assertThat(new JdbcRepositorioUmbrales(dataSource).vigentes(new InstanciaId(999_999L))).isEmpty();
    }
}
