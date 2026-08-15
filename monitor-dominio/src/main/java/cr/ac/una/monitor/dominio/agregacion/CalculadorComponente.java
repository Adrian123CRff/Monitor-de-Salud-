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
 * IP_usuarios / IP_fondo (ADR 0006) no se separan aquí: este calculador
 * siempre produce un único Indicador por Componente. MuestrearInstanciaServicio
 * lo llama dos veces para PROCESOS (una por UmbralesIniciales.procesosUsuarios(),
 * otra por procesosFondo()) y combina el resultado con CombinadorSubIndicadores.
 *
 * PENDIENTE: los vetos absolutos de agregacion.md (a2/a7/a8 forzando
 * CRITICO sin importar el promedio, tablespace >= 98 %...) no están
 * implementados: hoy solo bajan la puntuación de ARCHIVOS como cualquier
 * otra variable.
 */
public final class CalculadorComponente {

    public Indicador calcular(Muestra muestra, Componente componente, List<Umbral> umbrales) {
        Map<String, Double> puntuaciones = new LinkedHashMap<>();
        double sumaPonderada = 0;
        double sumaPesos = 0;

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
        }

        if (sumaPesos <= 0) {
            throw new IllegalStateException(
                "Ninguna variable de " + componente + " coincidió entre la muestra y los umbrales configurados. "
                + "Variables en la muestra: " + muestra.valores().keySet());
        }

        return new Indicador(componente, sumaPonderada / sumaPesos, puntuaciones);
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
