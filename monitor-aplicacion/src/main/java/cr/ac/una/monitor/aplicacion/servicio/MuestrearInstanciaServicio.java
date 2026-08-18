package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
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
import cr.ac.una.monitor.dominio.agregacion.CalculadorComponente;
import cr.ac.una.monitor.dominio.agregacion.CalculadorDelta;
import cr.ac.una.monitor.dominio.agregacion.CombinadorSubIndicadores;
import cr.ac.una.monitor.dominio.agregacion.MotorIndicadores;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.alertas.AlertasIniciales;
import cr.ac.una.monitor.dominio.alertas.ConfirmadorTemporal;
import cr.ac.una.monitor.dominio.alertas.EvaluadorNivel;
import cr.ac.una.monitor.dominio.alertas.MotorAlertas;
import cr.ac.una.monitor.dominio.alertas.Nivel;
import cr.ac.una.monitor.dominio.alertas.ResultadoEvaluacion;
import cr.ac.una.monitor.dominio.alertas.UmbralAlerta;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Caso de uso central: recolecta procesos (usuarios + fondo)/memoria/archivos
 * de una instancia, los guarda en el histórico, y calcula el ISBD del momento.
 *
 * IP ya no es un único indicador plano: se calcula IP_usuarios (V$SESSION) e
 * IP_fondo (DBW0/LGWR/CKPT/PMON/SMON, ver ADR 0006) por separado y se
 * combinan con CombinadorSubIndicadores.
 *
 * Memoria: m8_over_alloc_delta y m10_multipass_delta se calculan aquí
 * (CalculadorDelta), consultando la última muestra guardada ANTES de
 * guardar la nueva -- esta orquestación con histórico es justo lo que un
 * recolector (que solo lee el estado crudo actual) no debe hacer.
 *
 * Fondo: b2_lgwr_espera_avg/b3_dbwr_espera_avg/b4_ckpt_switch_incompleto
 * tenían el mismo problema que memoria (AVERAGE_WAIT/TOTAL_WAITS de
 * V$SYSTEM_EVENT son acumulados desde el arranque, no delta-ables
 * directamente) -- se corrige igual: el recolector trae los contadores
 * crudos (TIME_WAITED/TOTAL_WAITS/switch count) y aquí se calcula la delta
 * del intervalo contra RepositorioMuestrasFondo (tabla propia, ver esa
 * interfaz, porque Componente.PROCESOS es ambiguo entre usuarios y fondo).
 *
 * Fallo por componente ("recolectarSeguro" de la skill de arquitectura): un
 * RecoleccionFallidaException no tumba todo el muestreo. Ese componente
 * queda Optional.empty() -- no se persiste, no entra en el cálculo -- y
 * MotorIndicadores lo excluye del promedio (peso redistribuido) sin
 * vetar, marcando el ISBD como parcial. Lo mismo aplica un nivel más abajo
 * para IP: si falla solo procesos_usuarios o solo procesos_fondo, IP se
 * calcula igual con el sub-indicador que sí llegó (CombinadorSubIndicadores
 * ya redistribuye pesos entre lo que reciba); IP entero queda ausente solo
 * si fallan los dos.
 *
 * Detalle por tablespace (MONITOR_TABLESPACE): se recolecta y persiste solo
 * si el agregado de archivos se pudo leer -- si Oracle no responde para el
 * agregado, tampoco va a responder para el detalle, no vale la pena
 * intentarlo dos veces. Es información complementaria para el dashboard,
 * no entra en el cálculo del ISBD (CalculadorComponente ya usa el peor
 * tablespace vía a4_peor_tablespace_pct); su propio fallo no debe tumbar
 * el resto del ciclo, por eso también pasa por recolectarSeguro.
 *
 * El propio Isbd se persiste en RepositorioIndices (MONITOR_INDICES,
 * existía la tabla desde V1 sin usar) al final de cada ciclo -- lo que
 * hace posible ConsultarSalud (último calculado, sin forzar un muestreo
 * nuevo) y ConsultarHistorico, ambos puerto/entrada sin implementación
 * hasta ahora.
 *
 * Alertas (dominio.alertas, MONITOR_ALERTAS): se evalúan las cinco
 * variables de AlertasIniciales. a2_datafiles_offline y peor_tablespace_pct
 * (por tablespace) solo si el agregado de archivos se pudo leer este ciclo
 * -- mismo criterio que el detalle de tablespaces. b1_procesos_caidos,
 * p6_sesiones_bloqueadas y m8_over_alloc_delta se evalúan junto con
 * fondo/procesos/memoria respectivamente, cada una condicionada solo a que
 * su propio componente se haya recolectado. Cada
 * evaluación consulta la alerta abierta (si hay), calcula el nivel con
 * histéresis (EvaluadorNivel) y deja que MotorAlertas decida abrir/cerrar/
 * escalar (ver ResultadoEvaluacion). Para las variables con confirmación
 * (ver UmbralAlerta.conConfirmacion()/ConfirmadorTemporal), el disparo
 * inicial (NORMAL -> algo distinto) además exige que N de las últimas M
 * muestras (RepositorioMuestras.ultimasN) hayan cruzado el umbral -- una
 * vez abierto el episodio, escalar/cerrar es inmediato.
 */
@Service
public class MuestrearInstanciaServicio implements MuestrearInstancia {

    private static final Logger log = LoggerFactory.getLogger(MuestrearInstanciaServicio.class);

    private final RecolectorProcesos procesosUsuarios;
    private final RecolectorProcesosFondo procesosFondo;
    private final RecolectorMemoria memoria;
    private final RecolectorArchivos archivos;
    private final RepositorioMuestras muestras;
    private final RepositorioMuestrasFondo muestrasFondo;
    private final RepositorioTablespaces tablespaces;
    private final RepositorioIndices indices;
    private final RepositorioAlertas alertas;
    private final RepositorioCalibracion calibraciones;
    private final CalculadorComponente calculador = new CalculadorComponente();
    private final CombinadorSubIndicadores combinador = new CombinadorSubIndicadores();
    private final MotorIndicadores motor = new MotorIndicadores();
    private final MotorAlertas motorAlertas = new MotorAlertas();

    public MuestrearInstanciaServicio(RecolectorProcesos procesosUsuarios, RecolectorProcesosFondo procesosFondo,
            RecolectorMemoria memoria, RecolectorArchivos archivos,
            RepositorioMuestras muestras, RepositorioMuestrasFondo muestrasFondo,
            RepositorioTablespaces tablespaces, RepositorioIndices indices, RepositorioAlertas alertas,
            RepositorioCalibracion calibraciones) {
        this.procesosUsuarios = procesosUsuarios;
        this.procesosFondo = procesosFondo;
        this.memoria = memoria;
        this.archivos = archivos;
        this.muestras = muestras;
        this.muestrasFondo = muestrasFondo;
        this.tablespaces = tablespaces;
        this.indices = indices;
        this.alertas = alertas;
        this.calibraciones = calibraciones;
    }

    @Override
    public Isbd ejecutar(InstanciaId instancia) {
        Calibracion calibracionVigente = calibracionVigenteOInicial();

        Optional<Muestra> muestraUsuarios =
            recolectarSeguro("procesos-usuarios", () -> procesosUsuarios.recolectar(instancia));
        Optional<Muestra> muestraFondo = recolectarSeguro("procesos-fondo", () -> procesosFondo.recolectar(instancia))
            .map(cruda -> conDeltasFondo(instancia, cruda));
        Optional<Muestra> muestraMemoria = recolectarSeguro("memoria", () -> memoria.recolectar(instancia))
            .map(cruda -> conDeltas(instancia, cruda));
        Optional<Muestra> muestraArchivos = recolectarSeguro("archivos", () -> archivos.recolectar(instancia));

        muestraUsuarios.ifPresent(m -> {
            muestras.guardar(instancia, m);
            evaluarAlertaSesionesBloqueadas(instancia, m);
        });
        muestraFondo.ifPresent(m -> {
            muestrasFondo.guardar(instancia, m);
            evaluarAlertaProcesosCaidos(instancia, m);
        });
        muestraMemoria.ifPresent(m -> {
            muestras.guardar(instancia, m);
            evaluarAlertaPresionPga(instancia, m);
        });
        muestraArchivos.ifPresent(m -> {
            muestras.guardar(instancia, m);
            // Optional, no .orElse(List.of()): hace falta distinguir "la
            // recolección falló, no sé nada este ciclo" de "recolectó bien, y
            // ahora mismo no hay tablespaces" -- solo en el primer caso NO se
            // debe reconciliar alertas abiertas (ver evaluarAlertas), o un
            // fallo transitorio cerraría alertas reales por error.
            Optional<List<DetalleTablespace>> detalleTablespaces =
                recolectarSeguro("tablespaces", () -> archivos.recolectarTablespaces(instancia));
            detalleTablespaces.ifPresent(detalle -> {
                if (!detalle.isEmpty()) {
                    tablespaces.guardar(instancia, m.momento(), detalle);
                }
                evaluarAlertas(instancia, m, detalle);
            });
        });

        Optional<Indicador> ipUsuarios = muestraUsuarios.map(
            m -> calculador.calcular(m, Componente.PROCESOS, UmbralesIniciales.procesosUsuarios()));
        Optional<Indicador> ipFondo = muestraFondo.map(
            m -> calculador.calcular(m, Componente.PROCESOS, UmbralesIniciales.procesosFondo()));
        Optional<Indicador> ip = combinarIp(ipUsuarios, ipFondo);

        Optional<Indicador> im = muestraMemoria.map(
            m -> calculador.calcular(m, Componente.MEMORIA, UmbralesIniciales.memoria()));
        Optional<Indicador> ia = muestraArchivos.map(
            m -> calculador.calcular(m, Componente.ARCHIVOS, UmbralesIniciales.archivos()));

        Isbd isbd = motor.calcular(Instant.now(), ip, im, ia, calibracionVigente);
        indices.guardar(instancia, isbd);
        return isbd;
    }

    /**
     * IP_usuarios/IP_fondo redistribuyen peso entre sí igual que MotorIndicadores
     * hace con PROCESOS/MEMORIA/ARCHIVOS -- si solo uno de los dos llegó, IP se
     * calcula con ese único sub-indicador. Solo si fallan ambos, IP entero queda
     * ausente y así llega a MotorIndicadores (excluido, sin vetar).
     */
    private Optional<Indicador> combinarIp(Optional<Indicador> usuarios, Optional<Indicador> fondo) {
        Map<String, Indicador> presentes = new LinkedHashMap<>();
        Map<String, Double> pesos = new LinkedHashMap<>();
        usuarios.ifPresent(i -> {
            presentes.put("usuarios", i);
            pesos.put("usuarios", UmbralesIniciales.PESO_IP_USUARIOS);
        });
        fondo.ifPresent(i -> {
            presentes.put("fondo", i);
            pesos.put("fondo", UmbralesIniciales.PESO_IP_FONDO);
        });
        if (presentes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(combinador.combinar(Componente.PROCESOS, presentes, pesos));
    }

    /** Ver AlertasIniciales: datafiles offline (binaria) y peor_tablespace_pct por tablespace. */
    private void evaluarAlertas(InstanciaId instancia, Muestra muestraArchivos, List<DetalleTablespace> detalle) {
        evaluarAlerta(instancia, Componente.ARCHIVOS, AlertasIniciales.datafilesOffline(),
            muestraArchivos.valor("a2_datafiles_offline"), Optional.empty(),
            valor -> "Datafiles offline: %d (umbral: 1).".formatted((int) valor));

        for (DetalleTablespace ts : detalle) {
            evaluarAlerta(instancia, Componente.ARCHIVOS, AlertasIniciales.peorTablespacePct(),
                ts.usedPercent(), Optional.of(ts.nombre()),
                valor -> "Tablespace %s al %.1f%%.".formatted(ts.nombre(), valor));
        }

        cerrarAlertasDeTablespacesQueYaNoExisten(instancia, detalle);
    }

    /**
     * Encontrado por auditoría externa (ver docs/): una alerta de tablespace
     * podía quedar abierta para siempre si el tablespace que la disparó
     * desaparece (renombrado, dropeado) -- el bucle de arriba solo evalúa
     * los tablespaces que SÍ vienen en la recolección actual, nunca revisa
     * si algo que estaba abierto ya no aparece. Solo se llama cuando la
     * recolección de este ciclo tuvo éxito (ver ejecutar(): detalleTablespaces.
     * ifPresent(...)), nunca cuando falló -- cerrar por un fallo transitorio
     * sería peor que el bug original.
     */
    private void cerrarAlertasDeTablespacesQueYaNoExisten(InstanciaId instancia, List<DetalleTablespace> detalle) {
        Instant ahora = Instant.now();
        Set<String> nombresActuales = detalle.stream().map(DetalleTablespace::nombre)
            .collect(Collectors.toSet());
        for (Alerta abierta : alertas.abiertas(instancia)) {
            if (abierta.variable().equals("peor_tablespace_pct")
                    && abierta.entidad().isPresent()
                    && !nombresActuales.contains(abierta.entidad().get())) {
                alertas.cerrar(abierta, ahora);
            }
        }
    }

    /**
     * Ver AlertasIniciales.procesosCaidos(): binaria y grave, igual que
     * datafilesOffline(). Complementa el veto absoluto que MotorIndicadores
     * ya aplica sobre IP_fondo -- ese veto solo describe el ciclo actual, no
     * queda ningún episodio con apertura/cierre en MONITOR_ALERTAS.
     */
    private void evaluarAlertaProcesosCaidos(InstanciaId instancia, Muestra muestraFondo) {
        evaluarAlerta(instancia, Componente.PROCESOS, AlertasIniciales.procesosCaidos(),
            muestraFondo.valor("b1_procesos_caidos"), Optional.empty(),
            valor -> "Procesos de fondo caídos: %d (DBW0/LGWR/CKPT/PMON/SMON).".formatted((int) valor));
    }

    /** Ver AlertasIniciales.sesionesBloqueadas(): confirmación 2 de 3. */
    private void evaluarAlertaSesionesBloqueadas(InstanciaId instancia, Muestra muestraUsuarios) {
        evaluarAlerta(instancia, Componente.PROCESOS, AlertasIniciales.sesionesBloqueadas(),
            muestraUsuarios.valor("p6_sesiones_bloqueadas"), Optional.empty(),
            valor -> "Sesiones bloqueadas: %d.".formatted((int) valor));
    }

    /**
     * Ver AlertasIniciales.presionPga(): confirmación 3 de 5, sobre la delta
     * del intervalo (conDeltas()), no el acumulado. Sin historial previo
     * (primera muestra de la instancia) la delta queda ausente -- no hay
     * nada que evaluar todavía este ciclo, igual que CalculadorComponente
     * la excluye del puntaje.
     */
    private void evaluarAlertaPresionPga(InstanciaId instancia, Muestra muestraMemoria) {
        Double delta = muestraMemoria.valores().get("m8_over_alloc_delta");
        if (delta == null) {
            return;
        }
        evaluarAlerta(instancia, Componente.MEMORIA, AlertasIniciales.presionPga(), delta, Optional.empty(),
            valor -> "Over-allocation de PGA: %.0f evento(s) nuevo(s) en este intervalo.".formatted(valor));
    }

    /**
     * Consulta la alerta abierta de (instancia, variable, entidad), calcula el
     * nivel candidato con histéresis contra el nivel anterior (NORMAL si no
     * había ninguna abierta, ver EvaluadorNivel), lo confirma si el umbral lo
     * exige (ver confirmarSiHaceFalta), y deja que MotorAlertas decida
     * abrir/cerrar/escalar.
     */
    private void evaluarAlerta(InstanciaId instancia, Componente componente, UmbralAlerta u, double valor,
            Optional<String> entidad, DoubleFunction<String> descripcion) {
        Optional<Alerta> abierta = alertas.buscarAbierta(instancia, u.variable(), entidad);
        Nivel nivelAnterior = abierta.map(Alerta::nivel).orElse(Nivel.NORMAL);
        Nivel nivelCandidato = EvaluadorNivel.evaluar(valor, nivelAnterior, u);
        Nivel nivelNuevo = confirmarSiHaceFalta(instancia, componente, u, nivelAnterior, nivelCandidato);
        Instant ahora = Instant.now();

        ResultadoEvaluacion resultado = motorAlertas.evaluar(nivelNuevo, abierta, () -> new Alerta(
            null, instancia, componente, u.variable(), entidad, nivelNuevo, valor, umbralDe(nivelNuevo, u),
            descripcion.apply(valor), ahora, Optional.empty()));

        switch (resultado) {
            case ResultadoEvaluacion.SinCambios ignored -> { }
            case ResultadoEvaluacion.Abrir r -> alertas.abrir(r.nueva());
            case ResultadoEvaluacion.Cerrar r -> alertas.cerrar(r.existente(), ahora);
            case ResultadoEvaluacion.CerrarYAbrir r -> {
                alertas.cerrar(r.aCerrar(), ahora);
                alertas.abrir(r.aAbrir());
            }
        }
    }

    /**
     * Solo el disparo inicial (NORMAL -> algo distinto) de una variable con
     * confirmación (ver UmbralAlerta.conConfirmacion()) queda sujeto a
     * ConfirmadorTemporal: si N de las últimas M muestras (la actual incluida,
     * ya persistida en este punto -- ver ejecutar()) no cruzaron
     * entradaAdvertencia, el candidato se descarta y el episodio se queda en
     * NORMAL este ciclo. Escalar o cerrar un episodio ya abierto no pasa por
     * aquí (nivelAnterior != NORMAL), ni las variables sin confirmación
     * (confirmacionesRequeridas() == 0, ConfirmadorTemporal.confirmada con
     * "0 de 0" siempre es true).
     */
    private Nivel confirmarSiHaceFalta(InstanciaId instancia, Componente componente, UmbralAlerta u,
            Nivel nivelAnterior, Nivel nivelCandidato) {
        if (nivelAnterior != Nivel.NORMAL || nivelCandidato == Nivel.NORMAL || u.confirmacionesRequeridas() == 0) {
            return nivelCandidato;
        }
        List<Muestra> ultimas = muestras.ultimasN(instancia, componente, u.ventanaConfirmacion());
        List<Boolean> cruzo = ultimas.stream()
            .map(m -> m.valores().getOrDefault(u.variable(), 0.0) >= u.entradaAdvertencia())
            .toList();
        boolean confirmada = ConfirmadorTemporal.confirmada(cruzo, u.confirmacionesRequeridas(), u.ventanaConfirmacion());
        return confirmada ? nivelCandidato : Nivel.NORMAL;
    }

    private double umbralDe(Nivel nivel, UmbralAlerta u) {
        return switch (nivel) {
            case CRITICO -> u.entradaCritico();
            case ALTO -> u.entradaAlto();
            case ADVERTENCIA, NORMAL -> u.entradaAdvertencia();
        };
    }

    /**
     * Un RecoleccionFallidaException es un dato de salud, no un error que tumbe
     * el ciclo entero: se registra y ese componente queda Optional.empty() para
     * este muestreo (ver javadoc de la clase).
     */
    private <T> Optional<T> recolectarSeguro(String etiqueta, Supplier<T> recoleccion) {
        try {
            return Optional.of(recoleccion.get());
        } catch (RecoleccionFallidaException e) {
            log.warn("Fallo recolectando {}: {}", etiqueta, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Duration.ZERO cuando no hay muestra anterior: CalculadorDelta.calcular
     * devuelve sinHistorial() antes de mirar el transcurrido en ese caso (el
     * Optional vacío se revisa primero), así que el valor exacto no importa,
     * solo evita un Optional adicional en cada llamada.
     */
    private Duration transcurridoDesde(Optional<Muestra> ultima, Muestra actual) {
        return ultima.map(m -> Duration.between(m.momento(), actual.momento())).orElse(Duration.ZERO);
    }

    /**
     * Añade m8_over_alloc_delta y m10_multipass_delta comparando contra la
     * última muestra de memoria guardada. Sin historial previo (primera
     * muestra de la instancia), las deltas quedan ausentes -- CalculadorComponente
     * reparte el peso entre pga_uso_pct únicamente, no revienta.
     */
    private Muestra conDeltas(InstanciaId instancia, Muestra cruda) {
        Optional<Muestra> ultima = muestras.ultima(instancia, Componente.MEMORIA);
        Duration transcurrido = transcurridoDesde(ultima, cruda);

        CalculadorDelta.Resultado deltaOverAlloc = CalculadorDelta.calcular(
            cruda.valor("m8_over_alloc_acum"),
            ultima.map(m -> m.valores().get("m8_over_alloc_acum")), transcurrido);
        CalculadorDelta.Resultado deltaMultipass = CalculadorDelta.calcular(
            cruda.valor("m10_multipass_acum"),
            ultima.map(m -> m.valores().get("m10_multipass_acum")), transcurrido);

        Map<String, Double> valores = new HashMap<>(cruda.valores());
        deltaOverAlloc.delta().ifPresent(d -> valores.put("m8_over_alloc_delta", d));
        deltaMultipass.delta().ifPresent(d -> valores.put("m10_multipass_delta", d));

        boolean reiniciada = deltaOverAlloc.reinicioDetectado() || deltaMultipass.reinicioDetectado();
        return new Muestra(Componente.MEMORIA, cruda.momento(), valores, reiniciada);
    }

    /**
     * Añade b2_lgwr_espera_avg/b3_dbwr_espera_avg/b4_ckpt_switch_incompleto
     * comparando contra la última muestra de fondo guardada. b2/b3 se
     * calculan como delta(time_waited)/delta(total_waits) del intervalo --
     * si no hubo esperas nuevas (delta(total_waits) == 0) se deja sin
     * puntuar en vez de dividir entre cero, igual que sin historial.
     * b1_procesos_caidos es instantáneo (no acumulado), pasa tal cual.
     */
    private Muestra conDeltasFondo(InstanciaId instancia, Muestra cruda) {
        Optional<Muestra> ultima = muestrasFondo.ultima(instancia);
        Duration transcurrido = transcurridoDesde(ultima, cruda);

        CalculadorDelta.Resultado deltaLgwrTw = CalculadorDelta.calcular(
            cruda.valor("b2_lgwr_time_waited_acum"),
            ultima.map(m -> m.valores().get("b2_lgwr_time_waited_acum")), transcurrido);
        CalculadorDelta.Resultado deltaLgwrN = CalculadorDelta.calcular(
            cruda.valor("b2_lgwr_total_waits_acum"),
            ultima.map(m -> m.valores().get("b2_lgwr_total_waits_acum")), transcurrido);
        CalculadorDelta.Resultado deltaDbwrTw = CalculadorDelta.calcular(
            cruda.valor("b3_dbwr_time_waited_acum"),
            ultima.map(m -> m.valores().get("b3_dbwr_time_waited_acum")), transcurrido);
        CalculadorDelta.Resultado deltaDbwrN = CalculadorDelta.calcular(
            cruda.valor("b3_dbwr_total_waits_acum"),
            ultima.map(m -> m.valores().get("b3_dbwr_total_waits_acum")), transcurrido);
        CalculadorDelta.Resultado deltaCkpt = CalculadorDelta.calcular(
            cruda.valor("b4_ckpt_switch_incompleto_acum"),
            ultima.map(m -> m.valores().get("b4_ckpt_switch_incompleto_acum")), transcurrido);

        Map<String, Double> valores = new HashMap<>(cruda.valores());

        if (deltaLgwrTw.delta().isPresent() && deltaLgwrN.delta().isPresent() && deltaLgwrN.delta().get() > 0) {
            valores.put("b2_lgwr_espera_avg", deltaLgwrTw.delta().get() / deltaLgwrN.delta().get());
        }
        if (deltaDbwrTw.delta().isPresent() && deltaDbwrN.delta().isPresent() && deltaDbwrN.delta().get() > 0) {
            valores.put("b3_dbwr_espera_avg", deltaDbwrTw.delta().get() / deltaDbwrN.delta().get());
        }
        deltaCkpt.delta().ifPresent(d -> valores.put("b4_ckpt_switch_incompleto", d));

        boolean reiniciada = deltaLgwrTw.reinicioDetectado() || deltaLgwrN.reinicioDetectado()
            || deltaDbwrTw.reinicioDetectado() || deltaDbwrN.reinicioDetectado() || deltaCkpt.reinicioDetectado();

        return new Muestra(Componente.PROCESOS, cruda.momento(), valores, reiniciada);
    }

    private Calibracion calibracionVigenteOInicial() {
        Calibracion vigente = calibraciones.vigente();
        return vigente != null ? vigente : Calibracion.inicial();
    }
}
