package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;

/** Dispara un ciclo de recolección + cálculo de indicadores para una instancia. */
public interface MuestrearInstancia {

    Isbd ejecutar(InstanciaId instancia);
}
