package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
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
 * PENDIENTE (deliberado, para no fingir robustez que no existe todavía):
 * no hay manejo de fallo por componente ("recolectarSeguro" de la skill de
 * arquitectura) -- si un recolector lanza RecoleccionFallidaException, todo
 * el muestreo falla en vez de marcar ese componente como DESCONOCIDO y
 * seguir con los otros dos. Es lo próximo que hay que endurecer antes de
 * dejarlo corriendo con el planificador automático. Tampoco se persiste
 * todavía la muestra de procesos de fondo (no hay tabla para ella).
 */
@Service
public class MuestrearInstanciaServicio implements MuestrearInstancia {

    private final RecolectorProcesos procesosUsuarios;
    private final RecolectorProcesosFondo procesosFondo;
    private final RecolectorMemoria memoria;
    private final RecolectorArchivos archivos;
    private final RepositorioMuestras muestras;
    private final RepositorioCalibracion calibraciones;
    private final CalculadorComponente calculador = new CalculadorComponente();
    private final CombinadorSubIndicadores combinador = new CombinadorSubIndicadores();
    private final MotorIndicadores motor = new MotorIndicadores();

    public MuestrearInstanciaServicio(RecolectorProcesos procesosUsuarios, RecolectorProcesosFondo procesosFondo,
            RecolectorMemoria memoria, RecolectorArchivos archivos,
            RepositorioMuestras muestras, RepositorioCalibracion calibraciones) {
        this.procesosUsuarios = procesosUsuarios;
        this.procesosFondo = procesosFondo;
        this.memoria = memoria;
        this.archivos = archivos;
        this.muestras = muestras;
        this.calibraciones = calibraciones;
    }

    @Override
    public Isbd ejecutar(InstanciaId instancia) {
        Calibracion calibracionVigente = calibracionVigenteOInicial();

        Muestra muestraUsuarios = procesosUsuarios.recolectar(instancia);
        Muestra muestraFondo = procesosFondo.recolectar(instancia);
        Muestra muestraMemoriaCruda = memoria.recolectar(instancia);
        Muestra muestraArchivos = archivos.recolectar(instancia);

        Muestra muestraMemoria = conDeltas(instancia, muestraMemoriaCruda);

        muestras.guardar(instancia, muestraUsuarios);
        // muestraFondo: pendiente, no hay tabla MONITOR_PROCESOS_FONDO todavía.
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

    private Calibracion calibracionVigenteOInicial() {
        Calibracion vigente = calibraciones.vigente();
        return vigente != null ? vigente : Calibracion.inicial();
    }
}
