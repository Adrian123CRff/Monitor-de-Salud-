package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.InstanciaResumen;

import java.util.List;

/** La vista general: todas las instancias activas, cada una con su último Isbd si ya existe. */
public interface ListarInstancias {

    List<InstanciaResumen> listar();
}
