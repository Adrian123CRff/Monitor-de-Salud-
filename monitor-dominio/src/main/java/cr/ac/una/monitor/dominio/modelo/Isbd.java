package cr.ac.una.monitor.dominio.modelo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El índice global de salud, con su estado y las causas que lo determinaron.
 * Ver ADR 0003: media aritmética ponderada de ip/im/ia, con veto explícito.
 *
 * ip/im/ia son Optional: un componente que no se pudo recolectar en este
 * ciclo (ver RecoleccionFallidaException) no se rellena con un valor
 * inventado -- se excluye del promedio (peso redistribuido entre los
 * presentes, ver MotorIndicadores) y parcial queda en true. "No sé" no es
 * lo mismo que "está mal": un componente ausente no dispara el veto.
 */
public record Isbd(Instant momento, double puntuacion, Estado estado,
                    Optional<Indicador> ip, Optional<Indicador> im, Optional<Indicador> ia,
                    boolean estadoPorVeto, List<String> causas, boolean parcial) {
}
