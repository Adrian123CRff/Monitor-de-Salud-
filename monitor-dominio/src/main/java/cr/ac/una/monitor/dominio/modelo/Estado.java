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

    public static Estado desdePuntuacion(double p) {
        for (Estado e : values()) {
            if (p >= e.min && (p < e.max || e == OPTIMO)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Puntuación fuera de [0,100]: " + p);
    }
}
