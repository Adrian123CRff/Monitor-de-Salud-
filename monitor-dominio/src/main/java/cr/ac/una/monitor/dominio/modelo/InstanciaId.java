package cr.ac.una.monitor.dominio.modelo;

/**
 * Identifica una instancia Oracle monitoreada (real o simulada, ver ADR 0001).
 * El dominio nunca sabe si detrás hay un contenedor, un PDB o un servidor real.
 */
public record InstanciaId(Long valor) {
}
