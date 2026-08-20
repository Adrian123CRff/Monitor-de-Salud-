package cr.ac.una.monitor.api.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Encontrado por auditoría externa (ver docs/, IsbdDto.vetusto): si el
 * planificador lleva varios ciclos sin poder correr, /salud sigue
 * devolviendo el último Isbd bueno sin avisar que ya no es reciente. 3x el
 * intervalo de muestreo (mismo margen que CalculadorDelta usa para "ciclo
 * perdido") evita marcar vetusto por un solo ciclo lento.
 *
 * Compartido entre SaludController (una instancia) y ResumenInstanciaDto (la
 * vista general) para que ambos usen exactamente el mismo umbral -- si no,
 * un tile podría verse "al día" en la vista general mientras el dashboard de
 * detalle de esa misma instancia ya lo marca vetusto.
 */
public final class CalculadorVetustez {

    private static final int MULTIPLICADOR = 3;

    private CalculadorVetustez() {
    }

    public static boolean esVetusto(Instant momento, Duration intervaloMuestreo) {
        Duration umbral = intervaloMuestreo.multipliedBy(MULTIPLICADOR);
        return Duration.between(momento, Instant.now()).compareTo(umbral) > 0;
    }
}
