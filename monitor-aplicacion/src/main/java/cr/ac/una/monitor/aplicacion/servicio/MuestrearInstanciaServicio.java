package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.dominio.agregacion.CalculadorComponente;
import cr.ac.una.monitor.dominio.agregacion.CalculadorDelta;
import cr.ac.una.monitor.dominio.agregacion.CombinadorSubIndicadores;
import cr.ac.una.monitor.dominio.agregacion.MotorIndicadores;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
 * PENDIENTE (deliberado, para no fingir robustez que no existe todavía):
 * no hay manejo de fallo por componente ("recolectarSeguro" de la skill de
 * arquitectura) -- si un recolector lanza RecoleccionFallidaException, todo
 * el muestreo falla en vez de marcar ese componente como DESCONOCIDO y
 * seguir con los otros dos. Es lo próximo que hay que endurecer antes de
 * dejarlo corriendo con el planificador automático.
 */
@Service
public class MuestrearInstanciaServicio implements MuestrearInstancia {

    private final RecolectorProcesos procesosUsuarios;
    private final RecolectorProcesosFondo procesosFondo;
    private final RecolectorMemoria memoria;
    private final RecolectorArchivos archivos;
    private final RepositorioMuestras muestras;
    private final RepositorioMuestrasFondo muestrasFondo;
    private final RepositorioCalibracion calibraciones;
    private final CalculadorComponente calculador = new CalculadorComponente();
    private final CombinadorSubIndicadores combinador = new CombinadorSubIndicadores();
    private final MotorIndicadores motor = new MotorIndicadores();

    public MuestrearInstanciaServicio(RecolectorProcesos procesosUsuarios, RecolectorProcesosFondo procesosFondo,
            RecolectorMemoria memoria, RecolectorArchivos archivos,
            RepositorioMuestras muestras, RepositorioMuestrasFondo muestrasFondo,
            RepositorioCalibracion calibraciones) {
        this.procesosUsuarios = procesosUsuarios;
        this.procesosFondo = procesosFondo;
        this.memoria = memoria;
        this.archivos = archivos;
        this.muestras = muestras;
        this.muestrasFondo = muestrasFondo;
        this.calibraciones = calibraciones;
    }

    @Override
    public Isbd ejecutar(InstanciaId instancia) {
        Calibracion calibracionVigente = calibracionVigenteOInicial();

        Muestra muestraUsuarios = procesosUsuarios.recolectar(instancia);
        Muestra muestraFondoCruda = procesosFondo.recolectar(instancia);
        Muestra muestraMemoriaCruda = memoria.recolectar(instancia);
        Muestra muestraArchivos = archivos.recolectar(instancia);

        Muestra muestraFondo = conDeltasFondo(instancia, muestraFondoCruda);
        Muestra muestraMemoria = conDeltas(instancia, muestraMemoriaCruda);

        muestras.guardar(instancia, muestraUsuarios);
        muestrasFondo.guardar(instancia, muestraFondo);
        muestras.guardar(instancia, muestraMemoria);
        muestras.guardar(instancia, muestraArchivos);

        Indicador ipUsuarios = calculador.calcular(
            muestraUsuarios, Componente.PROCESOS, UmbralesIniciales.procesosUsuarios());
        Indicador ipFondo = calculador.calcular(
            muestraFondo, Componente.PROCESOS, UmbralesIniciales.procesosFondo());
        Indicador ip = combinador.combinar(Componente.PROCESOS,
            Map.of("usuarios", ipUsuarios, "fondo", ipFondo),
            Map.of("usuarios", UmbralesIniciales.PESO_IP_USUARIOS, "fondo", UmbralesIniciales.PESO_IP_FONDO));

        Indicador im = calculador.calcular(muestraMemoria, Componente.MEMORIA, UmbralesIniciales.memoria());
        Indicador ia = calculador.calcular(muestraArchivos, Componente.ARCHIVOS, UmbralesIniciales.archivos());

        return motor.calcular(Instant.now(), ip, im, ia, calibracionVigente);
    }

    /**
     * Añade m8_over_alloc_delta y m10_multipass_delta comparando contra la
     * última muestra de memoria guardada. Sin historial previo (primera
     * muestra de la instancia), las deltas quedan ausentes -- CalculadorComponente
     * reparte el peso entre pga_uso_pct únicamente, no revienta.
     */
    private Muestra conDeltas(InstanciaId instancia, Muestra cruda) {
        Optional<Muestra> ultima = muestras.ultima(instancia, Componente.MEMORIA);

        CalculadorDelta.Resultado deltaOverAlloc = CalculadorDelta.calcular(
            cruda.valor("m8_over_alloc_acum"),
            ultima.map(m -> m.valores().get("m8_over_alloc_acum")));
        CalculadorDelta.Resultado deltaMultipass = CalculadorDelta.calcular(
            cruda.valor("m10_multipass_acum"),
            ultima.map(m -> m.valores().get("m10_multipass_acum")));

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

        CalculadorDelta.Resultado deltaLgwrTw = CalculadorDelta.calcular(
            cruda.valor("b2_lgwr_time_waited_acum"),
            ultima.map(m -> m.valores().get("b2_lgwr_time_waited_acum")));
        CalculadorDelta.Resultado deltaLgwrN = CalculadorDelta.calcular(
            cruda.valor("b2_lgwr_total_waits_acum"),
            ultima.map(m -> m.valores().get("b2_lgwr_total_waits_acum")));
        CalculadorDelta.Resultado deltaDbwrTw = CalculadorDelta.calcular(
            cruda.valor("b3_dbwr_time_waited_acum"),
            ultima.map(m -> m.valores().get("b3_dbwr_time_waited_acum")));
        CalculadorDelta.Resultado deltaDbwrN = CalculadorDelta.calcular(
            cruda.valor("b3_dbwr_total_waits_acum"),
            ultima.map(m -> m.valores().get("b3_dbwr_total_waits_acum")));
        CalculadorDelta.Resultado deltaCkpt = CalculadorDelta.calcular(
            cruda.valor("b4_ckpt_switch_incompleto_acum"),
            ultima.map(m -> m.valores().get("b4_ckpt_switch_incompleto_acum")));

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
