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
        List<String> causas) {

    public static IsbdDto desde(Isbd isbd) {
        return new IsbdDto(
            isbd.momento(),
            isbd.puntuacion(),
            isbd.estado().name(),
            isbd.ip().map(Indicador::puntuacion).orElse(null),
            isbd.im().map(Indicador::puntuacion).orElse(null),
            isbd.ia().map(Indicador::puntuacion).orElse(null),
            isbd.estadoPorVeto(),
            isbd.parcial(),
            isbd.causas());
    }
}
