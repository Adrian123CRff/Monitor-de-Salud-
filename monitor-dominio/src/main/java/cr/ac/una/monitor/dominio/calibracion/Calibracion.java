package cr.ac.una.monitor.dominio.calibracion;

import cr.ac.una.monitor.dominio.modelo.Componente;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Pesos y umbral de veto vigentes para combinar IP/IM/IA en el ISBD.
 * Arranca desde application.yml (ver monitor-api), recalibrable en caliente
 * vía RepositorioCalibracion (monitor-aplicacion) -- y ese endpoint no tiene
 * autenticación (ADR 0005), así que esta validación es la única defensa
 * real contra una calibración corrupta.
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
        // Encontrado por auditoría externa (ver docs/): sumar 1.0 no bastaba --
        // {0.0, 0.0, 1.0} pasaba, y con un componente ausente ese ciclo
        // (RecoleccionFallidaException) MotorIndicadores podía dividir entre una
        // suma de pesos presentes de 0, dando NaN. Un peso negativo, además,
        // podía hacer que el promedio ponderado saliera de [0,100] -- eso lo
        // atrapa Indicador/Isbd en la mayoría de los casos, pero no hace falta
        // llegar hasta ahí si nunca se acepta el peso negativo.
        if (!pesos.keySet().equals(Set.of(Componente.values()))) {
            throw new IllegalArgumentException(
                "La calibración debe traer un peso para cada componente (" + Arrays.toString(Componente.values())
                + "), recibido: " + pesos.keySet());
        }
        pesos.forEach((componente, peso) -> {
            if (peso == null || peso <= 0 || peso.isNaN()) {
                throw new IllegalArgumentException(
                    "El peso de " + componente + " debe ser mayor que 0, recibido: " + peso);
            }
        });
    }

    /** Pesos del documento de diseño, sección 17 (ADR 0003): 30/35/35, declarados como no calibrados. */
    public static Calibracion inicial() {
        return new Calibracion(
            Map.of(Componente.PROCESOS, 0.30, Componente.MEMORIA, 0.35, Componente.ARCHIVOS, 0.35),
            true, 40.0);
    }
}
