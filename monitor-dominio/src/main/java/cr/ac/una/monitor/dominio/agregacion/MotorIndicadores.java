package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Isbd;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Combina IP, IM e IA en el ISBD. ADR 0003: media aritmética ponderada,
 * con un veto que fuerza el estado a CRITICO si cualquier componente cae
 * por debajo del umbral configurado, sin importar el promedio.
 *
 * Los vetos absolutos de agregacion.md (tablespace >= 98 %, datafile en
 * RECOVER, fallo de recolección sostenido...) quedan pendientes: dependen
 * de los adaptadores Oracle reales, que aún no existen (ver monitor-infraestructura).
 */
public final class MotorIndicadores {

    public Isbd calcular(Instant momento, Indicador ip, Indicador im, Indicador ia, Calibracion cal) {
        Map<Componente, Indicador> indicadores = Map.of(
            Componente.PROCESOS, ip,
            Componente.MEMORIA, im,
            Componente.ARCHIVOS, ia);

        double puntuacion = aritmeticaPonderada(indicadores, cal.pesos());

        List<String> causas = new ArrayList<>();
        if (cal.vetoHabilitado()) {
            for (Indicador i : List.of(ip, im, ia)) {
                if (i.puntuacion() < cal.umbralVetoComponente()) {
                    causas.add("%s en %.0f (veto: < %.0f)".formatted(
                        i.componente(), i.puntuacion(), cal.umbralVetoComponente()));
                }
            }
        }

        boolean vetado = !causas.isEmpty();
        Estado estado = vetado ? Estado.CRITICO : Estado.desdePuntuacion(puntuacion);
        return new Isbd(momento, puntuacion, estado, ip, im, ia, vetado, List.copyOf(causas));
    }

    private double aritmeticaPonderada(Map<Componente, Indicador> indicadores, Map<Componente, Double> pesos) {
        double suma = 0;
        for (var entrada : indicadores.entrySet()) {
            Double peso = pesos.get(entrada.getKey());
            if (peso == null) {
                throw new IllegalArgumentException(
                    "Falta el peso de " + entrada.getKey() + " en la calibración vigente");
            }
            suma += peso * entrada.getValue().puntuacion();
        }
        return suma;
    }
}
