package cr.ac.una.monitor.dominio.modelo;

import java.util.Map;

/**
 * Puntuación de salud 0-100 de un componente (IP, IM o IA). 100 = sano.
 *
 * IP_usuarios / IP_fondo (ver ADR 0006) no viven aquí: son dos Indicador
 * de Componente.PROCESOS por separado, combinados en uno solo por
 * CombinadorSubIndicadores antes de llegar a MotorIndicadores.
 */
public record Indicador(Componente componente, double puntuacion,
                         Map<String, Double> puntuacionesPorVariable) {
    public Indicador {
        if (puntuacion < 0 || puntuacion > 100) {
            throw new IllegalArgumentException(
                "Un indicador es una puntuación de salud en [0,100], recibido: " + puntuacion
                + ". Si venía de una utilización, falta invertir la polaridad.");
        }
    }
}
