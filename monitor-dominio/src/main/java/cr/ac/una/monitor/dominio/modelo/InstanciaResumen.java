package cr.ac.una.monitor.dominio.modelo;

import java.util.Optional;

/**
 * Un tile de la vista general (pedido del profesor: "un dashboard principal
 * donde aparezcan todas las bases de datos", ver docs/plan-trabajo-pendiente.md
 * módulo F): la instancia del catálogo más su último Isbd calculado, si ya
 * hay uno. salud vacío es un catálogo sin ningún muestreo todavía (instancia
 * recién agregada), no un error -- distinto de un Isbd.parcial (que sí
 * describe un fallo de recolección de un ciclo concreto).
 */
public record InstanciaResumen(Instancia instancia, Optional<Isbd> salud) {
}
