package cr.ac.una.monitor.dominio.modelo;

import java.time.Instant;
import java.util.List;

/**
 * El índice global de salud, con su estado y las causas que lo determinaron.
 * Ver ADR 0003: media aritmética ponderada de ip/im/ia, con veto explícito.
 */
public record Isbd(Instant momento, double puntuacion, Estado estado,
                    Indicador ip, Indicador im, Indicador ia,
                    boolean estadoPorVeto, List<String> causas) {
}
