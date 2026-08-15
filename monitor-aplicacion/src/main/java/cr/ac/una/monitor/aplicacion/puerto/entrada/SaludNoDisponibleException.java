package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;

/** Todavía no hay ningún Isbd calculado para esta instancia (RepositorioIndices vacío). */
public class SaludNoDisponibleException extends RuntimeException {

    public SaludNoDisponibleException(InstanciaId instancia) {
        super("Todavía no hay un ISBD calculado para la instancia " + instancia
            + " -- ejecuta un muestreo primero (POST /muestrear o esperá al planificador).");
    }
}
