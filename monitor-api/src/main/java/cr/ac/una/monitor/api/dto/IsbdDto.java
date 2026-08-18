package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Isbd;

import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para el ISBD -- nunca se serializa el record de dominio
 * directamente (ver skill de arquitectura, "Los DTO no son las clases del
 * dominio"). ip/im/ia quedan null cuando el componente no se pudo
 * recolectar ese ciclo (ver Isbd.parcial): el desglose por variable no
 * viaja aquí, monitor_indices solo guarda el agregado.
 *
 * vetusto (encontrado por auditoría externa, ver docs/): "parcial" dice si
 * ESTE ciclo tuvo componentes ausentes -- no dice nada de si el planificador
 * lleva varios ciclos sin correr (Docker caído, excepción no atrapada
 * deteniendo el @Scheduled, etc.), porque en ese caso no se persiste ningún
 * Isbd nuevo y /salud sigue devolviendo el último bueno sin más. "vetusto"
 * es una propiedad del momento de la consulta (¿cuánto pasó desde momento()
 * hasta ahora?), no del dato en sí -- por eso se calcula en el borde HTTP
 * (SaludController) en vez de vivir en el record de dominio Isbd, y por eso
 * el default aquí es false: /muestrear y /salud/historico no lo necesitan
 * (el primero siempre es fresco por definición, el segundo es histórico a
 * propósito).
 */
public record IsbdDto(
        Instant momento,
        double puntuacion,
        String estado,
        Double ip,
        Double im,
        Double ia,
        boolean estadoPorVeto,
        boolean parcial,
        boolean vetusto,
        List<String> causas) {

    public static IsbdDto desde(Isbd isbd) {
        return desde(isbd, false);
    }

    public static IsbdDto desde(Isbd isbd, boolean vetusto) {
        return new IsbdDto(
            isbd.momento(),
            isbd.puntuacion(),
            isbd.estado().name(),
            isbd.ip().map(Indicador::puntuacion).orElse(null),
            isbd.im().map(Indicador::puntuacion).orElse(null),
            isbd.ia().map(Indicador::puntuacion).orElse(null),
            isbd.estadoPorVeto(),
            isbd.parcial(),
            vetusto,
            isbd.causas());
    }
}
