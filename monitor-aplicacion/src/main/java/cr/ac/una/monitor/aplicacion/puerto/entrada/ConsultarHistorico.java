package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;

import java.time.Instant;
import java.util.List;

/** Serie temporal del ISBD de una instancia. Granularidad: pendiente P4 de detalle de implementación. */
public interface ConsultarHistorico {

    List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta);
}
