package cr.ac.una.monitor.dominio.modelo;

/**
 * Una fila de detalle por tablespace en un instante (ver MONITOR_TABLESPACE).
 * El agregado de ARCHIVOS (a4_peor_tablespace_pct) usa el PEOR tablespace
 * para puntuar -- ver CalculadorComponente y skill diseno-de-indicadores,
 * "el mismo principio dentro de cada componente" -- pero el dashboard
 * necesita ver todos, no solo el peor, para explicar el agregado.
 */
public record DetalleTablespace(String nombre, double usedPercent, double usedBytes, double maxBytes) {
}
