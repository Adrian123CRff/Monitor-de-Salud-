package cr.ac.una.monitor.dominio.modelo;

import java.util.Map;

/**
 * Puntuación de salud 0-100 de un componente (IP, IM o IA). 100 = sano.
 *
 * IP_usuarios / IP_fondo (ver ADR 0006) no viven aquí: son dos Indicador
 * de Componente.PROCESOS por separado, combinados en uno solo por
 * CombinadorSubIndicadores antes de llegar a MotorIndicadores.
 *
 * vetado: distingue "puntuación baja por promedio" de "puntuación en 0
 * porque una variable disparó un veto absoluto" (ver CalculadorComponente /
 * Umbral.vetoAbsoluto). Sin esta marca, un sub-indicador vetado (p. ej.
 * IP_fondo en 0 por un proceso mandatorio caído) se diluye como un número
 * más al combinarse con otros sub-indicadores en CombinadorSubIndicadores
 * -- IP_usuarios=100 + IP_fondo=0(vetado) con pesos 0.40/0.60 da IP=40.0,
 * que puede caer justo por encima o por debajo del umbral de veto de
 * MotorIndicadores según cómo se hayan calibrado ambos números, en vez de
 * vetar siempre que CUALQUIERA de los dos lo hizo. Encontrado por auditoría
 * externa (ver docs/), verificado con la calibración por defecto: dio
 * IP=40.0 exacto contra un umbral de 40.0, así que el veto por puntuación
 * no disparaba (comparación estricta). El constructor de 3 argumentos
 * existe para no obligar a los ~15 call sites de test/persistencia
 * (ninguno construye un Indicador ya vetado) a declarar `vetado=false`
 * explícitamente.
 */
public record Indicador(Componente componente, double puntuacion, boolean vetado,
                         Map<String, Double> puntuacionesPorVariable) {
    public Indicador {
        if (puntuacion < 0 || puntuacion > 100) {
            throw new IllegalArgumentException(
                "Un indicador es una puntuación de salud en [0,100], recibido: " + puntuacion
                + ". Si venía de una utilización, falta invertir la polaridad.");
        }
    }

    public Indicador(Componente componente, double puntuacion, Map<String, Double> puntuacionesPorVariable) {
        this(componente, puntuacion, false, puntuacionesPorVariable);
    }
}
