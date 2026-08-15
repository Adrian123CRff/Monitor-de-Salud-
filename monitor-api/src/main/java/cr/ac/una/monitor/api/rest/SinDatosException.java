package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

/** No hay ninguna muestra guardada todavía para este componente/instancia. */
public class SinDatosException extends RuntimeException {

    public SinDatosException(InstanciaId instancia, Componente componente) {
        super("No hay datos de " + componente + " para la instancia " + instancia
            + " todavía -- ejecuta un muestreo primero.");
    }
}
