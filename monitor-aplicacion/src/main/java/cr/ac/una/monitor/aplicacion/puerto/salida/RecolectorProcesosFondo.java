package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

/**
 * Lee el estado de los procesos de fondo mandatorios (DBW0, LGWR, CKPT,
 * PMON, SMON) -- ver ADR 0006. Deliberadamente separado de
 * RecolectorProcesos: la fuente de datos es distinta (V$BGPROCESS /
 * V$SYSTEM_EVENT, no V$SESSION / V$RESOURCE_LIMIT).
 * @throws RecoleccionFallidaException si no se pudo leer la instancia.
 */
public interface RecolectorProcesosFondo {

    Muestra recolectar(InstanciaId instancia);
}
