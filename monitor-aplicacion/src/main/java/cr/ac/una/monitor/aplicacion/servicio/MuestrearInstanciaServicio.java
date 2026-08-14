package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorMemoria;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorProcesos;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.dominio.agregacion.CalculadorComponente;
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

/**
 * Caso de uso central: recolecta procesos/memoria/archivos de una instancia,
 * los guarda en el histórico, y calcula el ISBD del momento.
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

    private final RecolectorProcesos procesos;
    private final RecolectorMemoria memoria;
    private final RecolectorArchivos archivos;
    private final RepositorioMuestras muestras;
    private final RepositorioCalibracion calibraciones;
    private final CalculadorComponente calculador = new CalculadorComponente();
    private final MotorIndicadores motor = new MotorIndicadores();

    public MuestrearInstanciaServicio(RecolectorProcesos procesos, RecolectorMemoria memoria,
            RecolectorArchivos archivos, RepositorioMuestras muestras, RepositorioCalibracion calibraciones) {
        this.procesos = procesos;
        this.memoria = memoria;
        this.archivos = archivos;
        this.muestras = muestras;
        this.calibraciones = calibraciones;
    }

    @Override
    public Isbd ejecutar(InstanciaId instancia) {
        Calibracion calibracionVigente = calibracionVigenteOInicial();

        Muestra muestraProcesos = procesos.recolectar(instancia);
        Muestra muestraMemoria = memoria.recolectar(instancia);
        Muestra muestraArchivos = archivos.recolectar(instancia);

        muestras.guardar(instancia, muestraProcesos);
        muestras.guardar(instancia, muestraMemoria);
        muestras.guardar(instancia, muestraArchivos);

        Indicador ip = calculador.calcular(muestraProcesos, Componente.PROCESOS, UmbralesIniciales.procesos());
        Indicador im = calculador.calcular(muestraMemoria, Componente.MEMORIA, UmbralesIniciales.memoria());
        Indicador ia = calculador.calcular(muestraArchivos, Componente.ARCHIVOS, UmbralesIniciales.archivos());

        return motor.calcular(Instant.now(), ip, im, ia, calibracionVigente);
    }

    private Calibracion calibracionVigenteOInicial() {
        Calibracion vigente = calibraciones.vigente();
        return vigente != null ? vigente : Calibracion.inicial();
    }
}
