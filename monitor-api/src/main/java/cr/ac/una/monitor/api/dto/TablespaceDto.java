package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;

/** DTO de respuesta para el detalle por tablespace (GET /tablespaces). */
public record TablespaceDto(String nombre, double usedPercent, double usedBytes, double maxBytes) {

    public static TablespaceDto desde(DetalleTablespace d) {
        return new TablespaceDto(d.nombre(), d.usedPercent(), d.usedBytes(), d.maxBytes());
    }
}
