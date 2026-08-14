package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

/**
 * Lee m1..m9 (SGA/PGA) de una instancia.
 * @throws RecoleccionFallidaException si no se pudo leer la instancia.
 */
public interface RecolectorMemoria {

    Muestra recolectar(InstanciaId instancia);
}
