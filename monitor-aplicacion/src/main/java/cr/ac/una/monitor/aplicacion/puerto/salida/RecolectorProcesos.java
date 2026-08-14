package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

/**
 * Lee p1..p8 (IP_usuarios / IP_fondo, ver ADR y pendiente P4) de una instancia.
 * @throws RecoleccionFallidaException si no se pudo leer la instancia.
 */
public interface RecolectorProcesos {

    Muestra recolectar(InstanciaId instancia);
}
