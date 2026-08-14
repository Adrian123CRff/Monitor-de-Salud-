package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

/**
 * Lee a1..a8 (datafiles, tempfiles, redo logs, tablespaces) de una instancia.
 * @throws RecoleccionFallidaException si no se pudo leer la instancia.
 */
public interface RecolectorArchivos {

    Muestra recolectar(InstanciaId instancia);
}
