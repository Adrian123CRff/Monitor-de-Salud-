package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VariableEvaluada;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VistaComponente;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioUmbrales;
import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * El detalle que responde "cuál variable está fuera de límites" (pedido del
 * profesor), no solo "cuánto puntuó el componente".
 */
class ConsultarComponenteServicioTest {

    private static final InstanciaId INSTANCIA = new InstanciaId(1L);
    private static final Instant MOMENTO = Instant.parse("2026-08-20T10:00:00Z");

    private final RepositorioUmbrales umbralesDeDiseno = instancia -> UmbralesIniciales.porGrupo();

    private RepositorioMuestras muestrasCon(Componente componente, Muestra muestra) {
        return new RepositorioMuestras() {
            @Override
            public void guardar(InstanciaId i, Muestra m) { }

            @Override
            public Optional<Muestra> ultima(InstanciaId i, Componente c) {
                return c == componente ? Optional.of(muestra) : Optional.empty();
            }

            @Override
            public List<Muestra> enRango(InstanciaId i, Componente c, Instant desde, Instant hasta) {
                return List.of();
            }

            @Override
            public List<Muestra> ultimasN(InstanciaId i, Componente c, int n) {
                return List.of();
            }
        };
    }

    private static final RepositorioMuestrasFondo SIN_FONDO = new RepositorioMuestrasFondo() {
        @Override
        public void guardar(InstanciaId i, Muestra m) { }

        @Override
        public Optional<Muestra> ultima(InstanciaId i) {
            return Optional.empty();
        }
    };

    /** El caso real de esta instancia: IA en 90 y el culpable es redundancia_redo. */
    @Test
    void ordena_las_variables_por_lo_que_le_estan_costando_al_componente() {
        Muestra archivos = new Muestra(Componente.ARCHIVOS, MOMENTO, Map.of(
            "peor_tablespace_pct", 0.03,
            "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 1.0   // 1 miembro por grupo: sin copia -> puntua 0
        ), false);

        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.ARCHIVOS, archivos), SIN_FONDO, umbralesDeDiseno);

        VistaComponente vista = servicio.detalle(INSTANCIA, Componente.ARCHIVOS).get("actual");

        assertThat(vista.puntuacion()).isCloseTo(90.0, offset(0.01));

        // La peor primero: redundancia_redo puntua 0 con peso 0.10 -> se lleva 10 puntos.
        VariableEvaluada peor = vista.variables().get(0);
        assertThat(peor.variable()).isEqualTo("redundancia_redo");
        assertThat(peor.puntuacion()).isCloseTo(0.0, offset(0.01));
        assertThat(peor.valor()).isEqualTo(1.0);
        assertThat(peor.aportePerdido()).isCloseTo(10.0, offset(0.01));

        // Y el resto no le cuesta nada al componente.
        assertThat(vista.variables().subList(1, vista.variables().size()))
            .allSatisfy(v -> assertThat(v.aportePerdido()).isCloseTo(0.0, offset(0.01)));
    }

    @Test
    void marca_la_variable_que_disparo_el_veto_absoluto() {
        Muestra archivos = new Muestra(Componente.ARCHIVOS, MOMENTO, Map.of(
            "peor_tablespace_pct", 40.0,
            "a2_datafiles_offline", 1.0,   // veto absoluto
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 2.0
        ), false);

        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.ARCHIVOS, archivos), SIN_FONDO, umbralesDeDiseno);

        VistaComponente vista = servicio.detalle(INSTANCIA, Componente.ARCHIVOS).get("actual");

        assertThat(vista.puntuacion()).isCloseTo(0.0, offset(0.01));
        assertThat(vista.vetado()).isTrue();
        assertThat(vista.variables())
            .filteredOn(v -> v.variable().equals("a2_datafiles_offline"))
            .singleElement()
            .satisfies(v -> assertThat(v.disparoVeto()).isTrue());
        // Las demas no lo dispararon, aunque el componente entero este en 0.
        assertThat(vista.variables())
            .filteredOn(v -> v.variable().equals("peor_tablespace_pct"))
            .singleElement()
            .satisfies(v -> assertThat(v.disparoVeto()).isFalse());
    }

    @Test
    void marca_tambien_el_veto_por_valor_crudo_del_peor_tablespace() {
        Muestra archivos = new Muestra(Componente.ARCHIVOS, MOMENTO, Map.of(
            "peor_tablespace_pct", 99.0,   // >= 98 -> limite duro
            "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 2.0
        ), false);

        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.ARCHIVOS, archivos), SIN_FONDO, umbralesDeDiseno);

        VistaComponente vista = servicio.detalle(INSTANCIA, Componente.ARCHIVOS).get("actual");

        assertThat(vista.vetado()).isTrue();
        assertThat(vista.variables())
            .filteredOn(v -> v.variable().equals("peor_tablespace_pct"))
            .singleElement()
            .satisfies(v -> assertThat(v.disparoVeto()).isTrue());
    }

    /** Las variables de contexto siguen en el crudo, pero no en el desglose puntuado. */
    @Test
    void las_variables_de_contexto_no_aparecen_en_el_desglose_pero_si_en_el_crudo() {
        Muestra memoria = new Muestra(Componente.MEMORIA, MOMENTO, Map.of(
            "pga_uso_pct", 60.0,
            "m8_over_alloc_delta", 0.0,
            "m10_multipass_delta", 0.0,
            "m1_sga_total_bytes", 1_600_000_000.0,   // contexto: no tiene umbral
            "m9_cache_hit_pct", 100.0                 // descartada a proposito (ver UmbralesIniciales)
        ), false);

        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.MEMORIA, memoria), SIN_FONDO, umbralesDeDiseno);

        VistaComponente vista = servicio.detalle(INSTANCIA, Componente.MEMORIA).get("actual");

        assertThat(vista.valores()).containsKeys("m1_sga_total_bytes", "m9_cache_hit_pct");
        assertThat(vista.variables()).extracting(VariableEvaluada::variable)
            .containsExactlyInAnyOrder("pga_uso_pct", "m8_over_alloc_delta", "m10_multipass_delta");
    }

    @Test
    void usa_los_umbrales_de_la_tabla_y_no_los_de_diseno() {
        Muestra memoria = new Muestra(Componente.MEMORIA, MOMENTO, Map.of("pga_uso_pct", 60.0), false);

        // Umbral calibrado: 60 ya es critico (con los de diseno, ok=90, daria 100).
        RepositorioUmbrales calibrados = instancia -> Map.of(
            GrupoUmbral.MEMORIA,
            List.of(Umbral.lineal("pga_uso_pct", TipoUmbral.LINEAL_INVERTIDA, 30, 50, 0.4)));

        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.MEMORIA, memoria), SIN_FONDO, calibrados);

        VistaComponente vista = servicio.detalle(INSTANCIA, Componente.MEMORIA).get("actual");

        assertThat(vista.puntuacion()).isCloseTo(0.0, offset(0.01));
        assertThat(vista.variables()).singleElement()
            .satisfies(v -> assertThat(v.puntuacion()).isCloseTo(0.0, offset(0.01)));
    }

    @Test
    void sin_muestras_devuelve_un_mapa_vacio_en_vez_de_reventar() {
        // Repositorio que nunca tiene nada: el 404 lo decide el borde HTTP,
        // no la aplicacion (ver ComponentesController).
        Muestra ninguna = new Muestra(Componente.PROCESOS, MOMENTO, Map.of(), false);
        var servicio = new ConsultarComponenteServicio(
            muestrasCon(Componente.PROCESOS, ninguna), SIN_FONDO, umbralesDeDiseno);

        assertThat(servicio.detalle(INSTANCIA, Componente.ARCHIVOS)).isEmpty();
    }
}
