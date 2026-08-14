package cr.ac.una.monitor.dominio.modelo;

import java.time.Instant;
import java.util.Map;

/** Una lectura cruda de un subsistema en un instante (p. ej. p1..p8, m1..m9, a1..a8). */
public record Muestra(
        Componente componente,
        Instant momento,
        Map<String, Double> valores,
        boolean instanciaReiniciada) {

    public double valor(String variable) {
        Double v = valores.get(variable);
        if (v == null) {
            throw new VariableAusenteException(componente, variable);
        }
        return v;
    }
}
