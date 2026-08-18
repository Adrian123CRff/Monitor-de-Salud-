package cr.ac.una.monitor.dominio.agregacion;

import java.time.Duration;
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
 *
 * transcurrido (encontrado por auditoría externa, ver docs/): la delta se
 * trata como incomparable, no como un número real, si el intervalo entre
 * las dos muestras es demasiado largo -- un ciclo perdido, un backend
 * reiniciado a mitad de intervalo, o simplemente una consulta lenta ese
 * ciclo, hacían que la siguiente delta cubriera 5, 10 o 30 minutos en vez
 * de uno, dando falsos "presión de PGA"/"over-allocation" mucho más altos
 * de lo real. No se normaliza a una tasa (delta/minuto): eso obligaría a
 * recalibrar todos los Umbral.penalizacion que ya asumen "delta del
 * intervalo configurado" como unidad -- más simple y más seguro descartar
 * la comparación cuando deja de tener sentido.
 */
public final class CalculadorDelta {

    /**
     * 3 minutos es generoso frente al intervalo de muestreo por defecto
     * (60s, application.yml monitor.muestreo.intervalo) sin que este módulo
     * (Java puro, sin Spring) necesite conocer el valor configurado -- cubre
     * un ciclo perdido puntual sin ser tan largo como para dejar pasar un
     * problema real de intervalos consistentemente más lentos.
     */
    private static final Duration INTERVALO_MAXIMO_COMPARABLE = Duration.ofMinutes(3);

    private CalculadorDelta() {
    }

    public record Resultado(Optional<Double> delta, boolean reinicioDetectado) {

        public static Resultado sinHistorial() {
            return new Resultado(Optional.empty(), false);
        }
    }

    public static Resultado calcular(double actual, Optional<Double> anterior, Duration transcurrido) {
        if (anterior.isEmpty()) {
            return Resultado.sinHistorial();
        }
        if (transcurrido.compareTo(INTERVALO_MAXIMO_COMPARABLE) > 0) {
            return Resultado.sinHistorial();
        }
        if (actual < anterior.get()) {
            return new Resultado(Optional.empty(), true);
        }
        return new Resultado(Optional.of(actual - anterior.get()), false);
    }
}
