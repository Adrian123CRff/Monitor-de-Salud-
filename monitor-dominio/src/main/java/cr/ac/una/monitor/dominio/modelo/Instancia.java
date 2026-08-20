package cr.ac.una.monitor.dominio.modelo;

/**
 * Una fila del catálogo MONITOR_INSTANCIA (ADR 0001): una base de datos
 * monitoreada, identificada y con un alias legible para mostrar en la vista
 * general. host/puerto/servicio/tipo son descriptivos hoy (ver
 * V6__instancia_inicial.sql) -- nada lee esos campos para decidir a dónde
 * conectarse todavía, así que este record solo trae lo que la vista general
 * necesita.
 */
public record Instancia(InstanciaId id, String alias) {
}
