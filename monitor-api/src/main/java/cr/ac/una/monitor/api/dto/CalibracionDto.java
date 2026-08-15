package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;

import java.util.Map;
import java.util.stream.Collectors;

/** DTO de lectura/escritura para GET y PUT /calibracion. */
public record CalibracionDto(Map<String, Double> pesos, boolean vetoHabilitado, double umbralVetoComponente) {

    public static CalibracionDto desde(Calibracion c) {
        Map<String, Double> pesos = c.pesos().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
        return new CalibracionDto(pesos, c.vetoHabilitado(), c.umbralVetoComponente());
    }

    public Calibracion aDominio() {
        Map<Componente, Double> pesosDominio = pesos.entrySet().stream()
            .collect(Collectors.toMap(e -> Componente.valueOf(e.getKey()), Map.Entry::getValue));
        return new Calibracion(pesosDominio, vetoHabilitado, umbralVetoComponente);
    }
}
