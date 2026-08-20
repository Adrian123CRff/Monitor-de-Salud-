package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VariableEvaluada;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VistaComponente;
import cr.ac.una.monitor.dominio.modelo.Estado;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detalle de un componente: el crudo de siempre, más el desglose de cuánto
 * puntuó cada variable.
 *
 * "valores" se mantiene tal cual estaba (todas las variables recolectadas,
 * incluidas las de contexto que no puntúan) para no romper a quien ya lo
 * consumía; "variables" es el agregado nuevo y solo trae las que SÍ puntúan,
 * ordenadas por lo que le están costando al componente.
 *
 * El "estado" de cada puntuación viaja resuelto desde aquí, igual que en
 * IsbdDto: la escala del §18 (90/75/60/40) se aplica en un solo lugar, el
 * dominio. Si el frontend la replicara para pintar estas filas, un cambio de
 * escala tendría que hacerse en dos sitios y nada avisaría si se olvida uno.
 */
public record DetalleComponenteDto(
        String componente,
        Instant momento,
        Map<String, Double> valores,
        Double puntuacion,
        String estado,
        Boolean vetado,
        List<VariableDto> variables) {

    public static DetalleComponenteDto desde(VistaComponente v) {
        return new DetalleComponenteDto(
            v.componente().name(),
            v.momento(),
            v.valores(),
            v.puntuacion(),
            estadoDe(v.puntuacion()),
            v.vetado(),
            v.variables().stream().map(VariableDto::desde).toList());
    }

    /**
     * aportePerdido viaja calculado en vez de dejar que el frontend lo derive:
     * es la cifra que ordena la lista, y si el cliente la recalculara con otra
     * fórmula el orden y el número mostrado podrían no coincidir.
     */
    public record VariableDto(
            String variable,
            Double valor,
            double puntuacion,
            String estado,
            double pesoEnComponente,
            double aportePerdido,
            boolean disparoVeto) {

        static VariableDto desde(VariableEvaluada v) {
            return new VariableDto(v.variable(), v.valor(), v.puntuacion(),
                Estado.desdePuntuacion(v.puntuacion()).name(),
                v.pesoEnComponente(), v.aportePerdido(), v.disparoVeto());
        }
    }

    /** null cuando la muestra no se pudo puntuar: sin puntuación no hay estado. */
    private static String estadoDe(Double puntuacion) {
        return puntuacion == null ? null : Estado.desdePuntuacion(puntuacion).name();
    }
}
