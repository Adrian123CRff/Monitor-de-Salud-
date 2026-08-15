package cr.ac.una.monitor.dominio.alertas;

/**
 * Severidad de una condición evaluada contra un UmbralAlerta. NORMAL nunca
 * se persiste en MONITOR_ALERTAS (el CHECK de esa tabla solo admite
 * ADVERTENCIA/ALTO/CRITICO) -- NORMAL significa "no hay alerta abierta".
 */
public enum Nivel {
    NORMAL, ADVERTENCIA, ALTO, CRITICO
}
