package cr.ac.una.monitor.dominio.agregacion;

import java.util.Optional;

/**
 * Calcula la delta de un contador acumulado desde el arranque de la
 * instancia (m8_over_alloc_acum, m10_multipass_acum...) entre dos
 * muestras consecutivas. Ver skill oracle-vistas-dinamicas/references/sql-memoria.md
 * "El problema de los acumulados".
 *
 * LIMITACIÓN CONOCIDA (documentada, no oculta): detecta el reinicio de la
 * instancia únicamente por la bajada del contador (actual &lt; anterior).
 * Si la instancia se reinicia y el contador ya volvió a subir por encima
 * del valor previo antes del siguiente muestreo, no se detecta -- la skill
 * lo señala explícitamente como el caso que solo `startup_time` resuelve.
 * El esquema actual no persiste `startup_time`, así que esta detección más
 * fina queda pendiente para cuando haga falta (P: aún no ha ocurrido un
 * reinicio real durante las pruebas de este proyecto).
 */
public final class CalculadorDelta {

    private CalculadorDelta() {
    }

    public record Resultado(Optional<Double> delta, boolean reinicioDetectado) {

        public static Resultado sinHistorial() {
            return new Resultado(Optional.empty(), false);
        }
    }

    public static Resultado calcular(double actual, Optional<Double> anterior) {
        if (anterior.isEmpty()) {
            return Resultado.sinHistorial();
        }
        if (actual < anterior.get()) {
            return new Resultado(Optional.empty(), true);
        }
        return new Resultado(Optional.of(actual - anterior.get()), false);
    }
}
