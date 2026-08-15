package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.modelo.Muestra;

import java.time.Instant;
import java.util.Map;

/** DTO de respuesta para el detalle crudo de un componente (GET /componentes/{c}). */
public record MuestraDto(String componente, Instant momento, Map<String, Double> valores) {

    public static MuestraDto desde(Muestra m) {
        return new MuestraDto(m.componente().name(), m.momento(), m.valores());
    }
}
