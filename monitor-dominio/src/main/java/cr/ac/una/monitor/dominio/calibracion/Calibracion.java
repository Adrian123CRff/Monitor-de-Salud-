package cr.ac.una.monitor.dominio.calibracion;

import cr.ac.una.monitor.dominio.modelo.Componente;

import java.util.Map;

/**
 * Pesos y umbral de veto vigentes para combinar IP/IM/IA en el ISBD.
 * Arranca desde application.yml (ver monitor-api), recalibrable en caliente
 * vía RepositorioCalibracion (monitor-aplicacion).
 */
public record Calibracion(
        Map<Componente, Double> pesos,
        boolean vetoHabilitado,
        double umbralVetoComponente) {

    public Calibracion {
        pesos = Map.copyOf(pesos);
        double suma = pesos.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(suma - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                "Los pesos de procesos, memoria y archivos deben sumar 1.0, suman " + suma);
        }
    }

    /** Pesos del documento de diseño, sección 17 (ADR 0003): 30/35/35, declarados como no calibrados. */
    public static Calibracion inicial() {
        return new Calibracion(
            Map.of(Componente.PROCESOS, 0.30, Componente.MEMORIA, 0.35, Componente.ARCHIVOS, 0.35),
            true, 40.0);
    }
}
