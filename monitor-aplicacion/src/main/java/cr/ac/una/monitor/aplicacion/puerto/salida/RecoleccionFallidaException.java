package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

/**
 * No se pudo leer un componente de una instancia. Es un dato de salud, no solo
 * un error técnico: el dashboard debe marcar el componente como DESCONOCIDO
 * (ver registro de decisiones), no ocultar el fallo ni reusar el último valor.
 */
public class RecoleccionFallidaException extends RuntimeException {

    public RecoleccionFallidaException(Componente componente, InstanciaId instancia, Throwable causa) {
        super("No se pudo recolectar " + componente + " de la instancia " + instancia, causa);
    }
}
