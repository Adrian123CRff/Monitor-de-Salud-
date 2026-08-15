package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.alertas.Alerta;

import java.time.Instant;

/** DTO de respuesta para GET /alertas. */
public record AlertaDto(
        Long id,
        String componente,
        String variable,
        String entidad,
        String nivel,
        double valor,
        double umbral,
        String descripcion,
        Instant abiertaEn) {

    public static AlertaDto desde(Alerta a) {
        return new AlertaDto(a.id(), a.componente().name(), a.variable(), a.entidad().orElse(null),
            a.nivel().name(), a.valor(), a.umbral(), a.descripcion(), a.abiertaEn());
    }
}
