package cr.ac.una.monitor.dominio.modelo;

/**
 * Escala de 5 estados definida en la sección 18 del documento de diseño.
 * El umbral de veto (ADR 0003) usa el límite inferior de CRITICO (40).
 */
public enum Estado {
    OPTIMO(90, 100), SALUDABLE(75, 90), ADVERTENCIA(60, 75),
    DEGRADADO(40, 60), CRITICO(0, 40);

    private final double min;
    private final double max;

    Estado(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Tolerancia para el ruido de punto flotante de sumar dobles ponderados
     * (0.30·100 + 0.35·100 + 0.35·100 no siempre da exactamente 100.0 en
     * IEEE 754) -- mucho más chica que cualquier desviación real (p. ej. la
     * puntuación de 150 que motivó la guarda de abajo), así que no oculta un
     * error genuino, solo evita que "100.00000000000001" cuente como uno.
     */
    private static final double EPSILON = 1e-6;

    public static Estado desdePuntuacion(double p) {
        // Guarda explícita, no solo el fallthrough del for: sin esto, p=150 caía
        // en la rama "e == OPTIMO" (que nunca revisa el límite superior, a
        // propósito, para que 100.0 exacto cuente como ÓPTIMO) y el for
        // devolvía OPTIMO en vez de lanzar. Encontrado por auditoría externa
        // (ver docs/): con Indicador ya validando [0,100] y Calibracion ya
        // exigiendo pesos > 0, este caso no debería ser alcanzable hoy -- pero
        // es la última línea de defensa si algo cambia, y NaN (p. ej. una
        // división entre cero) merece el mismo error explícito.
        if (Double.isNaN(p) || p < -EPSILON || p > 100 + EPSILON) {
            throw new IllegalArgumentException("Puntuación fuera de [0,100]: " + p);
        }
        double acotada = Math.min(100, Math.max(0, p));
        for (Estado e : values()) {
            if (acotada >= e.min && (acotada < e.max || e == OPTIMO)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Puntuación fuera de [0,100]: " + p);
    }
}
