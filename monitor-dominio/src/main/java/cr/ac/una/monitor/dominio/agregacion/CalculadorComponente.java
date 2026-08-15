package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import cr.ac.una.monitor.dominio.normalizacion.Normalizador;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combina las variables derivadas de una muestra en un único Indicador
 * (IP, IM o IA): normaliza cada variable según su Umbral y promedia
 * ponderando por peso_en_componente.
 *
 * Vetos absolutos (ver Umbral.vetoAbsoluto/vetoSiValorSupera y skill
 * diseno-de-indicadores / references/agregacion.md, "Parte 2 -- Reglas de
 * veto"): si alguna variable dispara su condición de veto, la puntuación
 * de TODO el componente se fuerza a 0, sin importar el promedio -- un
 * datafile OFFLINE, un proceso mandatorio caído o un tablespace al 98%
 * no admiten "un poco". El promedio ponderado normal se sigue calculando
 * y queda en puntuacionesPorVariable para que el dashboard explique el
 * porqué del 0, pero no es lo que se devuelve como puntuación cuando hay
 * veto. MotorIndicadores no necesita saber nada de esto: su veto de
 * componente existente (puntuación < umbralVetoComponente) atrapa el 0
 * igual que atraparía cualquier otro valor bajo.
 *
 * IP_usuarios / IP_fondo (ADR 0006) no se separan aquí: este calculador
 * siempre produce un único Indicador por Componente. MuestrearInstanciaServicio
 * lo llama dos veces para PROCESOS (una por UmbralesIniciales.procesosUsuarios(),
 * otra por procesosFondo()) y combina el resultado con CombinadorSubIndicadores.
 */
public final class CalculadorComponente {

    public Indicador calcular(Muestra muestra, Componente componente, List<Umbral> umbrales) {
        Map<String, Double> puntuaciones = new LinkedHashMap<>();
        double sumaPonderada = 0;
        double sumaPesos = 0;
        boolean vetoAbsoluto = false;

        for (Umbral u : umbrales) {
            if (u.tipo() == TipoUmbral.CONTEXTO) {
                continue;
            }
            Double crudo = muestra.valores().get(u.variable());
            if (crudo == null) {
                continue; // esta muestra no trae esa variable (adaptador parcial, por ejemplo)
            }
            double score = puntuar(crudo, u);
            puntuaciones.put(u.variable(), score);
            sumaPonderada += score * u.pesoEnComponente();
            sumaPesos += u.pesoEnComponente();

            if (u.vetoAbsoluto() && score <= 0.0) {
                vetoAbsoluto = true;
            }
            if (u.vetoSiValorSupera().isPresent() && crudo >= u.vetoSiValorSupera().get()) {
                vetoAbsoluto = true;
            }
        }

        if (sumaPesos <= 0) {
            throw new IllegalStateException(
                "Ninguna variable de " + componente + " coincidió entre la muestra y los umbrales configurados. "
                + "Variables en la muestra: " + muestra.valores().keySet());
        }

        double puntuacion = vetoAbsoluto ? 0.0 : sumaPonderada / sumaPesos;
        return new Indicador(componente, puntuacion, puntuaciones);
    }

    private double puntuar(double valor, Umbral u) {
        return switch (u.tipo()) {
            case LINEAL_INVERTIDA -> Normalizador.linealInvertida(valor, u.valorOk(), u.valorCritico());
            case LINEAL_DIRECTA -> Normalizador.linealDirecta(valor, u.valorCritico(), u.valorOk());
            case PENALIZACION_DISCRETA -> Normalizador.penalizacionDiscreta((int) valor, u.puntosPorEvento(), 0);
            case CRITICO_SI_HAY_ALGUNO -> Normalizador.criticoSiHayAlguno((int) valor);
            case CONTEXTO -> throw new IllegalStateException("CONTEXTO no debería llegar aquí, se filtra antes");
        };
    }
}
