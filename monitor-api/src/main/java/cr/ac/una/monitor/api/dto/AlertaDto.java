package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.alertas.Alerta;

import java.time.Instant;

/**
 * DTO de respuesta para GET /alertas.
 *
 * cerradaEn viaja en null mientras el episodio sigue abierto. Antes no existía
 * porque el endpoint solo devolvía abiertas -- desde que también sirve rangos
 * (para las franjas del gráfico de evolución) hace falta saber dónde termina
 * cada episodio, o todos se dibujarían llegando hasta el borde derecho.
 */
public record AlertaDto(
        Long id,
        String componente,
        String variable,
        String entidad,
        String nivel,
        double valor,
        double umbral,
        String descripcion,
        Instant abiertaEn,
        Instant cerradaEn) {

    public static AlertaDto desde(Alerta a) {
        return new AlertaDto(a.id(), a.componente().name(), a.variable(), a.entidad().orElse(null),
            a.nivel().name(), a.valor(), a.umbral(), a.descripcion(), a.abiertaEn(),
            a.cerradaEn().orElse(null));
    }
}
