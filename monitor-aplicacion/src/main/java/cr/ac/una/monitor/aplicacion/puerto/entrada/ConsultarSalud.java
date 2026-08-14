package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;

/** Devuelve el último ISBD calculado para una instancia (sin forzar un nuevo muestreo). */
public interface ConsultarSalud {

    Isbd actual(InstanciaId instancia);
}
