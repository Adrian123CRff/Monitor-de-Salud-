package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

import java.util.List;

/**
 * Lee a1..a8 (datafiles, tempfiles, redo logs, tablespaces) de una instancia.
 * @throws RecoleccionFallidaException si no se pudo leer la instancia.
 */
public interface RecolectorArchivos {

    Muestra recolectar(InstanciaId instancia);

    /**
     * Detalle por tablespace (ver MONITOR_TABLESPACE) -- recolectar() solo trae el
     * agregado (a4_peor_tablespace_pct) que necesita CalculadorComponente; esto es
     * lo que necesita el dashboard para mostrar todos los tablespaces, no solo el peor.
     *
     * Default en lista vacía a propósito: mantiene RecolectorArchivos como interfaz
     * funcional para los fakes de test que solo necesitan recolectar(); el adaptador
     * real (JdbcRecolectorArchivos) sí lo sobreescribe.
     */
    default List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
        return List.of();
    }
}
