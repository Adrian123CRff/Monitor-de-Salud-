package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Valida el flujo completo (paso 2 del orden de construcción sugerido:
 * "puertos + un adaptador falso") con adaptadores simulados en memoria,
 * sin depender de Oracle ni Postgres reales.
 */
class MuestrearInstanciaServicioTest {

    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

    private static final RecolectorProcesosFondo FONDO_SANO = instancia -> new Muestra(
        Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 0.0,
            "b2_lgwr_espera_avg", 0.0,
            "b3_dbwr_espera_avg", 0.0,
            "b4_ckpt_switch_incompleto", 0.0
        ), false);

    private static final RecolectorProcesosFondo FONDO_CRITICO = instancia -> new Muestra(
        Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 1.0,   // un proceso mandatorio caído -> veta esta variable
            "b2_lgwr_espera_avg", 15.0,  // por encima del crítico (10)
            "b3_dbwr_espera_avg", 15.0,
            "b4_ckpt_switch_incompleto", 10.0
        ), false);

    private final List<Muestra> muestrasGuardadas = new ArrayList<>();
    private final RepositorioMuestras repositorioMuestrasFalso = new RepositorioMuestras() {
        @Override
        public void guardar(InstanciaId instancia, Muestra muestra) {
            muestrasGuardadas.add(muestra);
        }

        @Override
        public Optional<Muestra> ultima(InstanciaId instancia, Componente componente) {
            return Optional.empty();
        }

        @Override
        public List<Muestra> enRango(InstanciaId instancia, Componente componente, Instant desde, Instant hasta) {
            return List.of();
        }
    };

    private final RepositorioCalibracion calibracionFalsa = new RepositorioCalibracion() {
        @Override
        public Calibracion vigente() {
            return Calibracion.inicial();
        }

        @Override
        public void registrar(Calibracion nueva) {
            // no-op para este test
        }
    };

    @Test
    void una_instancia_totalmente_sana_da_isbd_optimo_sin_veto() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0,
            "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0,
            "bloqueo_max_seg", 0.0
        ), false);

        // Sin historial previo (RepositorioMuestras falso siempre vacío), las deltas
        // quedan ausentes -- IM se basa solo en pga_uso_pct, que ya alcanza 100.
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0,
            "m8_over_alloc_acum", 1000.0,
            "m10_multipass_acum", 0.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0,
            "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(
            procesosSanos, FONDO_SANO, memoriaSana, archivosSanos, repositorioMuestrasFalso, calibracionFalsa);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.OPTIMO);
        assertThat(isbd.estadoPorVeto()).isFalse();
        // procesos de fondo aún no se persisten (pendiente), por eso 3 y no 4.
        assertThat(muestrasGuardadas).hasSize(3);
    }

    @Test
    void procesos_criticos_veta_el_isbd_aunque_memoria_y_archivos_esten_perfectos() {
        RecolectorProcesos procesosCriticos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 95.0,   // 0 puntos
            "util_sesiones_pct", 95.0,   // 0 puntos
            "p6_sesiones_bloqueadas", 4.0,  // 0 puntos (100 - 4*25)
            "bloqueo_max_seg", 200.0     // 0 puntos
        ), false);

        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(
            procesosCriticos, FONDO_CRITICO, memoriaSana, archivosSanos, repositorioMuestrasFalso, calibracionFalsa);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.ip().puntuacion()).isCloseTo(0.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.estadoPorVeto()).isTrue();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("PROCESOS"));
    }

    @Test
    void la_delta_de_memoria_se_calcula_contra_la_ultima_muestra_guardada_y_se_persiste() {
        Muestra ultimaMemoria = new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "m8_over_alloc_acum", 100.0,
            "m10_multipass_acum", 5.0
        ), false);

        List<Muestra> guardadas = new ArrayList<>();
        RepositorioMuestras repositorioConHistorial = new RepositorioMuestras() {
            @Override
            public void guardar(InstanciaId instancia, Muestra muestra) {
                guardadas.add(muestra);
            }

            @Override
            public Optional<Muestra> ultima(InstanciaId instancia, Componente componente) {
                return componente == Componente.MEMORIA ? Optional.of(ultimaMemoria) : Optional.empty();
            }

            @Override
            public List<Muestra> enRango(InstanciaId instancia, Componente componente, Instant desde, Instant hasta) {
                return List.of();
            }
        };

        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);

        // over_alloc sube 5 -> penalización 20 pts/evento -> 100-5*20=0 puntos.
        // multipass no se mueve -> delta 0 -> 100 puntos.
        RecolectorMemoria memoriaConPresionDePga = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0,
            "m8_over_alloc_acum", 105.0,
            "m10_multipass_acum", 5.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(
            procesosSanos, FONDO_SANO, memoriaConPresionDePga, archivosSanos, repositorioConHistorial, calibracionFalsa);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        // IM = 0.4*100 (pga_uso_pct sano) + 0.3*0 (over_alloc con presión) + 0.3*100 (multipass sin cambio) = 70
        assertThat(isbd.im().puntuacion()).isCloseTo(70.0, offset(0.01));

        Muestra memoriaGuardada = guardadas.stream()
            .filter(m -> m.componente() == Componente.MEMORIA)
            .findFirst().orElseThrow();
        assertThat(memoriaGuardada.valores().get("m8_over_alloc_delta")).isCloseTo(5.0, offset(0.01));
        assertThat(memoriaGuardada.valores().get("m10_multipass_delta")).isCloseTo(0.0, offset(0.01));
    }
}
