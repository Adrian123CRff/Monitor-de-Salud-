package cr.ac.una.monitor.dominio.modelo;

import java.util.Map;

/**
 * Puntuación de salud 0-100 de un componente (IP, IM o IA). 100 = sano.
 *
 * PENDIENTE (P4 del registro de decisiones): cómo se representan los
 * sub-índices IP_usuarios / IP_fondo dentro de IP. Se define en la sesión
 * de diseño con la skill diseno-de-indicadores; no se anticipa aquí.
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
