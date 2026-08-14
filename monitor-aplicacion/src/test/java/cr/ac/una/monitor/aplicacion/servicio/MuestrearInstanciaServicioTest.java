package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
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

        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0,
            "over_alloc_delta", 0.0,
            "cache_hit_pct_delta", 95.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0,
            "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(
            procesosSanos, memoriaSana, archivosSanos, repositorioMuestrasFalso, calibracionFalsa);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.OPTIMO);
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(muestrasGuardadas).hasSize(3);
    }

    @Test
    void procesos_criticos_veta_el_isbd_aunque_memoria_y_archivos_esten_perfectos() {
        // p6=4 bloqueos -> 100 - 4*25 = 0; util_procesos y util_sesiones sanos,
        // bloqueo_max_seg sano -> IP = (100+100+0+100)/4 = 75... insuficiente para
        // vetar por sí solo. Subimos la utilización también para forzar IP < 40.
        RecolectorProcesos procesosCriticos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 95.0,   // 0 puntos
            "util_sesiones_pct", 95.0,   // 0 puntos
            "p6_sesiones_bloqueadas", 4.0,  // 0 puntos (100 - 4*25)
            "bloqueo_max_seg", 200.0     // 0 puntos
        ), false);

        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "over_alloc_delta", 0.0, "cache_hit_pct_delta", 95.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(
            procesosCriticos, memoriaSana, archivosSanos, repositorioMuestrasFalso, calibracionFalsa);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.ip().puntuacion()).isCloseTo(0.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.estadoPorVeto()).isTrue();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("PROCESOS"));
    }
}
