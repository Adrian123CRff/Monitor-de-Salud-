package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combina varios sub-indicadores (por ejemplo IP_usuarios + IP_fondo, ver
 * ADR 0006) en un único Indicador de nivel superior, mediante media
 * aritmética ponderada -- el mismo principio que MotorIndicadores aplica un
 * nivel más arriba para IP/IM/IA. Las puntuacionesPorVariable de todos los
 * sub-indicadores se combinan en un solo mapa (con prefijo del sub-indicador)
 * para que el dashboard pueda mostrar el desglose completo.
 *
 * vetado se propaga con OR: si CUALQUIER sub-indicador está vetado (ver
 * CalculadorComponente), el combinado también lo está, sin importar qué
 * puntuación numérica resulte del promedio ponderado. Sin esto, un
 * IP_fondo=0 vetado por un proceso mandatorio caído se diluye contra
 * IP_usuarios=100 en una puntuación combinada que puede caer por encima
 * del umbral de veto de MotorIndicadores según los pesos -- el veto no
 * debe depender de esa coincidencia aritmética.
 */
public final class CombinadorSubIndicadores {

    public Indicador combinar(Componente componente, Map<String, Indicador> subIndicadoresConNombre,
            Map<String, Double> pesos) {
        double sumaPonderada = 0;
        double sumaPesos = 0;
        boolean vetado = false;
        Map<String, Double> variables = new LinkedHashMap<>();

        for (var entrada : subIndicadoresConNombre.entrySet()) {
            String nombre = entrada.getKey();
            Indicador sub = entrada.getValue();
            Double peso = pesos.get(nombre);
            if (peso == null) {
                throw new IllegalArgumentException("Falta el peso del sub-indicador '" + nombre + "'");
            }
            sumaPonderada += sub.puntuacion() * peso;
            sumaPesos += peso;
            vetado = vetado || sub.vetado();
            sub.puntuacionesPorVariable().forEach((variable, puntuacion) ->
                variables.put(nombre + "." + variable, puntuacion));
        }

        if (sumaPesos <= 0) {
            throw new IllegalStateException("No se proporcionó ningún sub-indicador para " + componente);
        }

        return new Indicador(componente, sumaPonderada / sumaPesos, vetado, variables);
    }
}
