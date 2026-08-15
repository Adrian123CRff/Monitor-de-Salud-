package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesosFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.dominio.agregacion.CalculadorComponente;
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
import java.util.Map;

/**
 * Caso de uso central: recolecta procesos (usuarios + fondo)/memoria/archivos
 * de una instancia, los guarda en el histórico, y calcula el ISBD del momento.
 *
 * IP ya no es un único indicador plano: se calcula IP_usuarios (V$SESSION) e
 * IP_fondo (DBW0/LGWR/CKPT/PMON/SMON, ver ADR 0006) por separado y se
 * combinan con CombinadorSubIndicadores.
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
        Muestra muestraMemoria = memoria.recolectar(instancia);
        Muestra muestraArchivos = archivos.recolectar(instancia);

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

    private Calibracion calibracionVigenteOInicial() {
        Calibracion vigente = calibraciones.vigente();
        return vigente != null ? vigente : Calibracion.inicial();
    }
}
