package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecoleccionFallidaException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioAlertas;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioTablespaces;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioUmbrales;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.alertas.Nivel;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    // Sin historial (RepositorioMuestrasFondo falso siempre vacío), b2/b3/b4 no
    // tienen delta que calcular contra nada y quedan sin puntuar -- solo b1
    // (instantáneo, no acumulado) se puntúa en la primera muestra.
    private static final RecolectorProcesosFondo FONDO_SANO = instancia -> new Muestra(
        Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 0.0,
            "b2_lgwr_time_waited_acum", 40.0,
            "b2_lgwr_total_waits_acum", 95.0,
            "b3_dbwr_time_waited_acum", 20.0,
            "b3_dbwr_total_waits_acum", 60.0,
            "b4_ckpt_switch_incompleto_acum", 0.0
        ), false);

    private static final RecolectorProcesosFondo FONDO_CRITICO = instancia -> new Muestra(
        Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 1.0,   // un proceso mandatorio caído -> veta esta variable
            "b2_lgwr_time_waited_acum", 40.0,
            "b2_lgwr_total_waits_acum", 95.0,
            "b3_dbwr_time_waited_acum", 20.0,
            "b3_dbwr_total_waits_acum", 60.0,
            "b4_ckpt_switch_incompleto_acum", 0.0
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

        @Override
        public List<Muestra> ultimasN(InstanciaId instancia, Componente componente, int n) {
            return List.of();
        }
    };

    private final RepositorioMuestrasFondo repositorioMuestrasFondoFalso = new RepositorioMuestrasFondo() {
        @Override
        public void guardar(InstanciaId instancia, Muestra muestra) {
            muestrasGuardadas.add(muestra);
        }

        @Override
        public Optional<Muestra> ultima(InstanciaId instancia) {
            return Optional.empty();
        }
    };

    private final List<DetalleTablespace> tablespacesGuardados = new ArrayList<>();
    private final RepositorioTablespaces repositorioTablespacesFalso = new RepositorioTablespaces() {
        @Override
        public void guardar(InstanciaId instancia, Instant momento, List<DetalleTablespace> detalle) {
            tablespacesGuardados.addAll(detalle);
        }

        @Override
        public List<DetalleTablespace> ultimo(InstanciaId instancia) {
            return List.copyOf(tablespacesGuardados);
        }
    };

    private final List<Isbd> indicesGuardados = new ArrayList<>();
    private final RepositorioIndices repositorioIndicesFalso = new RepositorioIndices() {
        @Override
        public void guardar(InstanciaId instancia, Isbd isbd) {
            indicesGuardados.add(isbd);
        }

        @Override
        public Optional<Isbd> ultimo(InstanciaId instancia) {
            return indicesGuardados.isEmpty()
                ? Optional.empty() : Optional.of(indicesGuardados.get(indicesGuardados.size() - 1));
        }

        @Override
        public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
            return List.copyOf(indicesGuardados);
        }
    };

    private final Map<String, Alerta> alertasAbiertas = new HashMap<>();
    private final List<Alerta> alertasAbiertasHistorial = new ArrayList<>();
    private final RepositorioAlertas repositorioAlertasFalso = new RepositorioAlertas() {
        private String clave(InstanciaId instancia, String variable, Optional<String> entidad) {
            return instancia.valor() + "|" + variable + "|" + entidad.orElse("");
        }

        @Override
        public Optional<Alerta> buscarAbierta(InstanciaId instancia, String variable, Optional<String> entidad) {
            return Optional.ofNullable(alertasAbiertas.get(clave(instancia, variable, entidad)));
        }

        @Override
        public Alerta abrir(Alerta nueva) {
            Alerta conId = new Alerta(1L + alertasAbiertasHistorial.size(), nueva.instancia(), nueva.componente(),
                nueva.variable(), nueva.entidad(), nueva.nivel(), nueva.valor(), nueva.umbral(),
                nueva.descripcion(), nueva.abiertaEn(), nueva.cerradaEn());
            alertasAbiertas.put(clave(nueva.instancia(), nueva.variable(), nueva.entidad()), conId);
            alertasAbiertasHistorial.add(conId);
            return conId;
        }

        @Override
        public void cerrar(Alerta existente, Instant cerradaEn) {
            alertasAbiertas.remove(clave(existente.instancia(), existente.variable(), existente.entidad()));
        }

        @Override
        public List<Alerta> abiertas(InstanciaId instancia) {
            return List.copyOf(alertasAbiertas.values());
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

    /**
     * Devuelve exactamente lo que la migración V8 siembra en el perfil
     * ESTANDAR, así que todas las puntuaciones esperadas de esta clase siguen
     * valiendo igual que cuando los umbrales estaban hardcodeados en el
     * servicio. Que la tabla real coincida con esta semilla es lo que prueba
     * JdbcRepositorioUmbralesIT contra Postgres.
     */
    private final RepositorioUmbrales umbralesFalsos = instancia -> UmbralesIniciales.porGrupo();

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

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosSanos, repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso,
            calibracionFalsa, umbralesFalsos);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.OPTIMO);
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(muestrasGuardadas).hasSize(4);
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

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosCriticos, FONDO_CRITICO,
            memoriaSana, archivosSanos, repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.ip().orElseThrow().puntuacion()).isCloseTo(0.0, offset(0.01));
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

            @Override
            public List<Muestra> ultimasN(InstanciaId instancia, Componente componente, int n) {
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

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO,
            memoriaConPresionDePga, archivosSanos, repositorioConHistorial, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        // IM = 0.4*100 (pga_uso_pct sano) + 0.3*0 (over_alloc con presión) + 0.3*100 (multipass sin cambio) = 70
        assertThat(isbd.im().orElseThrow().puntuacion()).isCloseTo(70.0, offset(0.01));

        Muestra memoriaGuardada = guardadas.stream()
            .filter(m -> m.componente() == Componente.MEMORIA)
            .findFirst().orElseThrow();
        assertThat(memoriaGuardada.valores().get("m8_over_alloc_delta")).isCloseTo(5.0, offset(0.01));
        assertThat(memoriaGuardada.valores().get("m10_multipass_delta")).isCloseTo(0.0, offset(0.01));
    }

    @Test
    void la_delta_de_fondo_se_calcula_contra_la_ultima_muestra_guardada_y_se_persiste() {
        Muestra ultimaFondo = new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "b2_lgwr_time_waited_acum", 40.0,
            "b2_lgwr_total_waits_acum", 95.0,
            "b3_dbwr_time_waited_acum", 20.0,
            "b3_dbwr_total_waits_acum", 60.0,
            "b4_ckpt_switch_incompleto_acum", 3.0
        ), false);

        List<Muestra> guardadas = new ArrayList<>();
        RepositorioMuestrasFondo repositorioFondoConHistorial = new RepositorioMuestrasFondo() {
            @Override
            public void guardar(InstanciaId instancia, Muestra muestra) {
                guardadas.add(muestra);
            }

            @Override
            public Optional<Muestra> ultima(InstanciaId instancia) {
                return Optional.of(ultimaFondo);
            }
        };

        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);

        // lgwr: delta_tw=10, delta_n=15 -> avg=0.666 (sano, ok=1). dbwr: delta_tw=180, delta_n=20 -> avg=9 (crítico, cerca de 10).
        RecolectorProcesosFondo fondoConEsperas = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "b1_procesos_caidos", 0.0,
            "b2_lgwr_time_waited_acum", 50.0,
            "b2_lgwr_total_waits_acum", 110.0,
            "b3_dbwr_time_waited_acum", 200.0,
            "b3_dbwr_total_waits_acum", 80.0,
            "b4_ckpt_switch_incompleto_acum", 5.0
        ), false);

        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, fondoConEsperas,
            memoriaSana, archivosSanos, repositorioMuestrasFalso, repositorioFondoConHistorial,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        Muestra fondoGuardada = guardadas.get(0);
        assertThat(fondoGuardada.valores().get("b2_lgwr_espera_avg")).isCloseTo(10.0 / 15.0, offset(0.001));
        assertThat(fondoGuardada.valores().get("b3_dbwr_espera_avg")).isCloseTo(9.0, offset(0.001));
        assertThat(fondoGuardada.valores().get("b4_ckpt_switch_incompleto")).isCloseTo(2.0, offset(0.01));
        assertThat(fondoGuardada.instanciaReiniciada()).isFalse();
    }

    @Test
    void un_componente_caido_no_tumba_el_ciclo_y_el_isbd_queda_parcial() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);

        // MEMORIA no responde este ciclo (p. ej. Oracle caído) -- RecoleccionFallidaException,
        // no una excepción cualquiera: es la que recolectarSeguro sabe atrapar.
        RecolectorMemoria memoriaCaida = instancia -> {
            throw new RecoleccionFallidaException(Componente.MEMORIA, instancia, new RuntimeException("ORA-12541"));
        };

        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO,
            memoriaCaida, archivosSanos, repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.parcial()).isTrue();
        assertThat(isbd.im()).isEmpty();
        assertThat(isbd.ip()).isPresent();
        assertThat(isbd.ia()).isPresent();
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("MEMORIA") && c.contains("fallo de recolección"));
        // ni monitor_memoria ni monitor_procesos_fondo reciben una fila este ciclo para MEMORIA --
        // solo se guardan procesos (usuarios), fondo y archivos: 3, no 4.
        assertThat(muestrasGuardadas).hasSize(3);
        assertThat(muestrasGuardadas).noneMatch(m -> m.componente() == Componente.MEMORIA);
    }

    @Test
    void el_detalle_por_tablespace_se_recolecta_y_persiste_junto_con_el_agregado_de_archivos() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);

        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        RecolectorArchivos archivosConDetalle = new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                return List.of(
                    new DetalleTablespace("SYSTEM", 40.0, 400.0, 1000.0),
                    new DetalleTablespace("USERS", 10.0, 100.0, 1000.0));
            }
        };

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConDetalle, repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso,
            calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        assertThat(tablespacesGuardados).hasSize(2);
        assertThat(tablespacesGuardados).extracting(DetalleTablespace::nombre)
            .containsExactlyInAnyOrder("SYSTEM", "USERS");
    }

    private RecolectorArchivos archivosConTablespace(double datafilesOffline, double usedPercentUsers) {
        return new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", usedPercentUsers, "a2_datafiles_offline", datafilesOffline,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                return List.of(new DetalleTablespace("USERS", usedPercentUsers, 100.0, 1000.0));
            }
        };
    }

    @Test
    void un_datafile_offline_abre_una_alerta_critica_de_inmediato() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConTablespace(1.0, 10.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        List<Alerta> abiertas = repositorioAlertasFalso.abiertas(INSTANCIA);
        assertThat(abiertas).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("a2_datafiles_offline");
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICO);
            assertThat(a.entidad()).isEmpty();
        });
    }

    /**
     * Regresión: al añadir la reconciliación de tablespaces huérfanos, toda la
     * evaluación de alertas de ARCHIVOS quedó dentro del ifPresent del detalle
     * por tablespace. Pero a2_datafiles_offline sale del AGREGADO, no del
     * detalle -- así que un fallo de DBA_TABLESPACE_USAGE_METRICS (consulta
     * aparte, más pesada, con timeout de 10s) se tragaba en silencio la alerta
     * de un datafile OFFLINE. El veto del ISBD seguía disparando, lo que hacía
     * el bug aún más difícil de ver: el dashboard mostraba CRITICO, pero el
     * panel de alertas no tenía nada y MONITOR_ALERTAS quedaba sin episodio.
     */
    @Test
    void un_datafile_offline_abre_alerta_aunque_falle_la_recoleccion_de_tablespaces() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        // El agregado SÍ se lee (y trae el datafile offline); el detalle por
        // tablespace es el que falla.
        RecolectorArchivos agregadoOkDetalleCaido = new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 1.0,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                throw new RecoleccionFallidaException(
                    Componente.ARCHIVOS, instancia, new RuntimeException("ORA-01013: timeout"));
            }
        };

        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana, agregadoOkDetalleCaido,
            repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso,
            repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA)).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("a2_datafiles_offline");
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICO);
        });
    }

    /**
     * La otra mitad de la misma decisión: las alertas POR TABLESPACE sí
     * dependen del detalle, y un fallo de recolección no debe cerrarlas
     * (cerrarAlertasDeTablespacesQueYaNoExisten no se llama en ese caso).
     */
    @Test
    void un_fallo_al_recolectar_tablespaces_no_cierra_las_alertas_de_tablespace_ya_abiertas() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        // Ciclo 1: USERS al 80% abre su alerta.
        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConTablespace(0.0, 80.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso,
            calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);
        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anyMatch(a -> a.variable().equals("peor_tablespace_pct"));

        // Ciclo 2: el detalle no responde. La alerta debe seguir abierta --
        // "no sé" no es lo mismo que "el tablespace ya no existe".
        RecolectorArchivos detalleCaido = new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", 80.0, "a2_datafiles_offline", 0.0,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                throw new RecoleccionFallidaException(
                    Componente.ARCHIVOS, instancia, new RuntimeException("ORA-01013: timeout"));
            }
        };

        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana, detalleCaido,
            repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso,
            repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anyMatch(a -> a.variable().equals("peor_tablespace_pct"));
    }

    @Test
    void un_proceso_de_fondo_caido_abre_una_alerta_critica_de_inmediato_ademas_del_veto_del_isbd() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);
        RecolectorArchivos archivosSanos = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
            "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
        ), false);

        // Con procesos de USUARIOS sanos (IP_usuarios=100) y solo el proceso de
        // FONDO caído, el combinado IP_usuarios*0.40 + IP_fondo(0)*0.60 = 40.0
        // exacto -- el mismo valor que el umbral de veto por defecto. Antes del
        // arreglo del veto propagado (Indicador.vetado, ver CalculadorComponente/
        // CombinadorSubIndicadores/MotorIndicadores) esta comparación estricta
        // (40.0 < 40.0 es falso) NO disparaba el veto: un proceso mandatorio
        // caído se reportaba SALUDABLE. Este test prueba las dos cosas a la vez:
        // que se abre el episodio en MONITOR_ALERTAS, y que el ISBD queda CRITICO.
        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_CRITICO,
            memoriaSana, archivosSanos, repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        Isbd isbd = servicio.ejecutar(INSTANCIA);

        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.estadoPorVeto()).isTrue();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("PROCESOS") && c.contains("vetado"));

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA)).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("b1_procesos_caidos");
            assertThat(a.componente()).isEqualTo(Componente.PROCESOS);
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICO);
            assertThat(a.entidad()).isEmpty();
        });
    }

    @Test
    void un_tablespace_que_sube_y_luego_baja_abre_y_cierra_la_alerta_con_histeresis() {
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConTablespace(0.0, 80.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);
        servicio.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anySatisfy(a -> assertThat(a.variable()).isEqualTo("peor_tablespace_pct"));

        // Baja a 72%: por debajo de la entrada (75) pero todavía por encima de la
        // salida (70) -- con histéresis, sigue abierta (no debería cerrar todavía).
        MuestrearInstanciaServicio servicioZonaMuerta = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO,
            memoriaSana, archivosConTablespace(0.0, 72.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);
        servicioZonaMuerta.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anySatisfy(a -> assertThat(a.variable()).isEqualTo("peor_tablespace_pct"));

        // Baja de la salida (70): ahora sí cierra.
        MuestrearInstanciaServicio servicioNormal = new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO,
            memoriaSana, archivosConTablespace(0.0, 50.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);
        servicioNormal.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .noneMatch(a -> a.variable().equals("peor_tablespace_pct"));
    }

    @Test
    void una_alerta_de_tablespace_se_cierra_si_el_tablespace_ya_no_aparece_en_la_recoleccion() {
        // Encontrado por auditoría externa: sin reconciliación, una alerta de
        // tablespace podía quedar abierta para siempre si el tablespace que la
        // disparó desaparece (renombrado, dropeado) -- el bucle de evaluación
        // solo mira lo que SÍ viene en la recolección actual.
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        // Ciclo 1: USERS al 80% abre la alerta.
        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConTablespace(0.0, 80.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos)
            .ejecutar(INSTANCIA);
        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anySatisfy(a -> assertThat(a.entidad()).contains("USERS"));

        // Ciclo 2: USERS ya no existe (renombrado a DATA) -- la recolección
        // funciona bien, solo que ya no incluye USERS.
        RecolectorArchivos archivosConOtroTablespace = new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                return List.of(new DetalleTablespace("DATA", 40.0, 400.0, 1000.0));
            }
        };
        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConOtroTablespace, repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos)
            .ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .noneMatch(a -> a.entidad().equals(Optional.of("USERS")));
    }

    @Test
    void un_fallo_al_recolectar_tablespaces_no_cierra_las_alertas_ya_abiertas() {
        // Lo contrario del test anterior: si la recolección FALLA (no si el
        // tablespace genuinamente desaparece), la reconciliación no debe
        // correr -- cerrar una alerta real por un fallo transitorio de red
        // sería peor que el bug original que arregla el test de arriba.
        RecolectorProcesos procesosSanos = instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0, "bloqueo_max_seg", 0.0
        ), false);
        RecolectorMemoria memoriaSana = instancia -> new Muestra(Componente.MEMORIA, Instant.now(), Map.of(
            "pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0
        ), false);

        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosConTablespace(0.0, 80.0), repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos)
            .ejecutar(INSTANCIA);
        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anySatisfy(a -> assertThat(a.entidad()).contains("USERS"));

        RecolectorArchivos archivosQueFallaElDetalle = new RecolectorArchivos() {
            @Override
            public Muestra recolectar(InstanciaId instancia) {
                return new Muestra(Componente.ARCHIVOS, Instant.now(), Map.of(
                    "peor_tablespace_pct", 80.0, "a2_datafiles_offline", 0.0,
                    "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0
                ), false);
            }

            @Override
            public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
                throw new RecoleccionFallidaException(Componente.ARCHIVOS, INSTANCIA,
                    new RuntimeException("ORA-12541: red caída"));
            }
        };
        new MuestrearInstanciaServicio(procesosSanos, FONDO_SANO, memoriaSana,
            archivosQueFallaElDetalle, repositorioMuestrasFalso, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos)
            .ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anySatisfy(a -> assertThat(a.entidad()).contains("USERS"));
    }

    /**
     * A diferencia de repositorioMuestrasFalso (siempre vacío), esta lleva un
     * historial real por componente para poder probar ConfirmadorTemporal --
     * guardar() lo actualiza (la más reciente primero), igual que
     * ultimasN/ultima lo devolverían de Postgres real.
     */
    private static class RepositorioMuestrasConHistorial implements RepositorioMuestras {
        private final Map<Componente, List<Muestra>> porComponente = new LinkedHashMap<>();

        void sembrar(Componente componente, Muestra... previas) {
            porComponente.computeIfAbsent(componente, k -> new ArrayList<>()).addAll(List.of(previas));
        }

        @Override
        public void guardar(InstanciaId instancia, Muestra muestra) {
            porComponente.computeIfAbsent(muestra.componente(), k -> new ArrayList<>()).add(0, muestra);
        }

        @Override
        public Optional<Muestra> ultima(InstanciaId instancia, Componente componente) {
            List<Muestra> historial = porComponente.get(componente);
            return historial == null || historial.isEmpty() ? Optional.empty() : Optional.of(historial.get(0));
        }

        @Override
        public List<Muestra> enRango(InstanciaId instancia, Componente componente, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<Muestra> ultimasN(InstanciaId instancia, Componente componente, int n) {
            List<Muestra> historial = porComponente.getOrDefault(componente, List.of());
            return historial.size() <= n ? List.copyOf(historial) : List.copyOf(historial.subList(0, n));
        }
    }

    private RecolectorProcesos procesosConSesionesBloqueadas(double p6) {
        return instancia -> new Muestra(Componente.PROCESOS, Instant.now(), Map.of(
            "util_procesos_pct", 30.0, "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", p6, "bloqueo_max_seg", 0.0
        ), false);
    }

    private static final RecolectorMemoria MEMORIA_SANA = instancia -> new Muestra(Componente.MEMORIA, Instant.now(),
        Map.of("pga_uso_pct", 60.0, "m8_over_alloc_acum", 1000.0, "m10_multipass_acum", 0.0), false);
    private static final RecolectorArchivos ARCHIVOS_SANOS = instancia -> new Muestra(Componente.ARCHIVOS, Instant.now(),
        Map.of("peor_tablespace_pct", 40.0, "a2_datafiles_offline", 0.0,
            "a7_archivos_invalidos", 0.0, "a8_archivos_recover", 0.0, "redundancia_redo", 2.0), false);

    @Test
    void sesiones_bloqueadas_una_sola_lectura_que_cruza_no_confirma_2_de_3_y_no_abre_alerta() {
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        // Dos lecturas previas SIN bloqueos -- junto con la actual (que sí
        // cruza) son 1 de 3, no alcanza la confirmación 2 de 3.
        repositorio.sembrar(Componente.PROCESOS,
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 0.0), false),
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 0.0), false));

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(2.0),
            FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS, repositorio, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .noneMatch(a -> a.variable().equals("p6_sesiones_bloqueadas"));
    }

    @Test
    void sesiones_bloqueadas_confirmadas_2_de_3_abre_la_alerta() {
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        // Una previa con bloqueo, otra sin -- junto con la actual (con bloqueo)
        // son 2 de 3: confirma.
        repositorio.sembrar(Componente.PROCESOS,
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 2.0), false),
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 0.0), false));

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(2.0),
            FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS, repositorio, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA)).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("p6_sesiones_bloqueadas");
            assertThat(a.nivel()).isEqualTo(Nivel.ADVERTENCIA);
        });
    }

    @Test
    void sesiones_bloqueadas_ya_abierta_escala_de_inmediato_sin_reconfirmar() {
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        repositorio.sembrar(Componente.PROCESOS,
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 2.0), false),
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 0.0), false));

        // Ciclo 1: confirma 2 de 3 y abre en ADVERTENCIA (igual que el test anterior).
        new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(2.0), FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS,
            repositorio, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso,
            repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);

        // Ciclo 2: una sola lectura en 6 (CRITICO) -- sin ninguna otra lectura
        // adicional que la confirme "2 de 3" en ese nivel. Como el episodio ya
        // estaba abierto (nivelAnterior != NORMAL), escala igual, de inmediato.
        new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(6.0), FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS,
            repositorio, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso,
            repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA)).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("p6_sesiones_bloqueadas");
            assertThat(a.nivel()).isEqualTo(Nivel.CRITICO);
        });
    }

    @Test
    void sesiones_bloqueadas_se_cierra_apenas_el_conteo_vuelve_a_cero() {
        // Regresión del bug encontrado preparando una prueba de estrés en vivo:
        // con salidaAdvertencia=0 y EvaluadorNivel usando < estricto, un valor de
        // 0 nunca cumplía "valor < 0" y el episodio quedaba abierto para siempre.
        // AlertasIniciales.sesionesBloqueadas() ahora usa entrada==salida==1 en
        // ese tramo -- este test prueba el ciclo completo abrir -> despejar -> cerrar.
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        repositorio.sembrar(Componente.PROCESOS,
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 2.0), false),
            new Muestra(Componente.PROCESOS, Instant.now(), Map.of("p6_sesiones_bloqueadas", 0.0), false));

        // Ciclo 1: confirma 2 de 3 y abre en ADVERTENCIA.
        new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(2.0), FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS,
            repositorio, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso,
            repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);
        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .anyMatch(a -> a.variable().equals("p6_sesiones_bloqueadas"));

        // Ciclo 2: el bloqueo se libera, vuelve a 0.
        new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0), FONDO_SANO, MEMORIA_SANA, ARCHIVOS_SANOS,
            repositorio, repositorioMuestrasFondoFalso, repositorioTablespacesFalso, repositorioIndicesFalso,
            repositorioAlertasFalso, calibracionFalsa, umbralesFalsos).ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .noneMatch(a -> a.variable().equals("p6_sesiones_bloqueadas"));
    }

    @Test
    void presion_de_pga_dos_de_cinco_no_confirma_y_no_abre_alerta() {
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        // Historial (la más reciente primero): la [0] trae m8_over_alloc_acum
        // para que conDeltas() calcule la delta del ciclo actual contra ella.
        repositorio.sembrar(Componente.MEMORIA,
            new Muestra(Componente.MEMORIA, Instant.now(),
                Map.of("m8_over_alloc_acum", 100.0, "m8_over_alloc_delta", 1.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 0.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 0.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 0.0), false));

        // acum sube de 100 a 101 -> delta actual = 1.0 (cruza). Junto con la
        // semilla [0] (delta 1.0, también cruza) son 2 de 5: no confirma 3 de 5.
        RecolectorMemoria memoriaConPresion = instancia -> new Muestra(Componente.MEMORIA, Instant.now(),
            Map.of("pga_uso_pct", 60.0, "m8_over_alloc_acum", 101.0, "m10_multipass_acum", 0.0), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0),
            FONDO_SANO, memoriaConPresion, ARCHIVOS_SANOS, repositorio, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA))
            .noneMatch(a -> a.variable().equals("m8_over_alloc_delta"));
    }

    @Test
    void presion_de_pga_confirmada_3_de_5_abre_la_alerta() {
        RepositorioMuestrasConHistorial repositorio = new RepositorioMuestrasConHistorial();
        repositorio.sembrar(Componente.MEMORIA,
            new Muestra(Componente.MEMORIA, Instant.now(),
                Map.of("m8_over_alloc_acum", 100.0, "m8_over_alloc_delta", 1.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 1.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 0.0), false),
            new Muestra(Componente.MEMORIA, Instant.now(), Map.of("m8_over_alloc_delta", 0.0), false));

        // acum sube de 100 a 101 -> delta actual = 1.0. Junto con las dos
        // semillas en 1.0 son 3 de 5: confirma.
        RecolectorMemoria memoriaConPresion = instancia -> new Muestra(Componente.MEMORIA, Instant.now(),
            Map.of("pga_uso_pct", 60.0, "m8_over_alloc_acum", 101.0, "m10_multipass_acum", 0.0), false);

        MuestrearInstanciaServicio servicio = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0),
            FONDO_SANO, memoriaConPresion, ARCHIVOS_SANOS, repositorio, repositorioMuestrasFondoFalso,
            repositorioTablespacesFalso, repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesFalsos);

        servicio.ejecutar(INSTANCIA);

        assertThat(repositorioAlertasFalso.abiertas(INSTANCIA)).anySatisfy(a -> {
            assertThat(a.variable()).isEqualTo("m8_over_alloc_delta");
            assertThat(a.nivel()).isEqualTo(Nivel.ADVERTENCIA);
        });
    }

    @Test
    void los_umbrales_de_la_tabla_mandan_sobre_los_valores_de_diseno() {
        // Mismo dato crudo (pga_uso_pct = 60) que en el test de instancia sana,
        // donde IM daba 100 con los umbrales de diseño (ok=90, critico=130).
        // Con estos umbrales -- los que traería una calibración real de una base
        // pequeña -- 60 ya está en zona crítica y IM cae a 0. Si el servicio
        // siguiera leyendo UmbralesIniciales, este test daría 100.
        RepositorioUmbrales umbralesCalibrados = instancia -> Map.of(
            GrupoUmbral.PROCESOS_USUARIOS, UmbralesIniciales.procesosUsuarios(),
            GrupoUmbral.PROCESOS_FONDO, UmbralesIniciales.procesosFondo(),
            GrupoUmbral.MEMORIA, List.of(
                Umbral.lineal("pga_uso_pct", TipoUmbral.LINEAL_INVERTIDA, 30, 50, 0.4)),
            GrupoUmbral.ARCHIVOS, UmbralesIniciales.archivos());

        Isbd isbd = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0), FONDO_SANO, MEMORIA_SANA,
            ARCHIVOS_SANOS, repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso,
            repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, umbralesCalibrados)
            .ejecutar(INSTANCIA);

        assertThat(isbd.im().orElseThrow().puntuacion()).isCloseTo(0.0, offset(0.01));
        // Y como MEMORIA cae bajo el umbral de veto (40), el ISBD entero queda CRITICO.
        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.estadoPorVeto()).isTrue();
    }

    @Test
    void un_grupo_ausente_en_la_tabla_cae_al_respaldo_en_codigo_sin_tumbar_el_ciclo() {
        // La tabla trae MEMORIA pero no los otros tres grupos (alguien borró
        // filas, o una migración a medias). Los que faltan usan UmbralesIniciales
        // y el ciclo se completa igual.
        RepositorioUmbrales soloMemoria = instancia -> Map.of(
            GrupoUmbral.MEMORIA, UmbralesIniciales.memoria());

        Isbd isbd = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0), FONDO_SANO, MEMORIA_SANA,
            ARCHIVOS_SANOS, repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso,
            repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, soloMemoria)
            .ejecutar(INSTANCIA);

        assertThat(isbd.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.OPTIMO);
        assertThat(isbd.parcial()).isFalse();
    }

    @Test
    void una_tabla_de_umbrales_completamente_vacia_no_impide_calcular_el_isbd() {
        RepositorioUmbrales vacio = instancia -> Map.of();

        Isbd isbd = new MuestrearInstanciaServicio(procesosConSesionesBloqueadas(0.0), FONDO_SANO, MEMORIA_SANA,
            ARCHIVOS_SANOS, repositorioMuestrasFalso, repositorioMuestrasFondoFalso, repositorioTablespacesFalso,
            repositorioIndicesFalso, repositorioAlertasFalso, calibracionFalsa, vacio)
            .ejecutar(INSTANCIA);

        assertThat(isbd.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.OPTIMO);
    }
}
