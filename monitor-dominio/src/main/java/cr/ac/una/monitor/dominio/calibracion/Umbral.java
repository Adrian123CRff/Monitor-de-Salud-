package cr.ac.una.monitor.dominio.calibracion;

import java.util.Optional;

/**
 * Configuración de normalización de una variable derivada (util_procesos_pct,
 * peor_tablespace_pct, etc. -- ver catalogo-variables.md "Variables derivadas").
 * Vive en tabla (monitor_umbral_puntuacion, ver RepositorioUmbrales y ADR
 * 0007), no en código. UmbralesIniciales es la semilla de esa tabla y el
 * respaldo si no responde; sus valores son de diseño, no resultados de
 * calibración.
 *
 * Dos mecanismos de veto absoluto (ver CalculadorComponente y skill
 * diseno-de-indicadores / references/agregacion.md, "Parte 2 -- Reglas de
 * veto"): condiciones donde no existe un "un poco", que fuerzan la
 * puntuación de TODO el componente a 0, no solo la de esta variable dentro
 * del promedio.
 *
 * - {@code vetoAbsoluto}: si la puntuación que ESTA variable calcula con su
 *   propio tipo/umbrales da 0, veta. Natural para CRITICO_SI_HAY_ALGUNO
 *   (datafile offline, proceso caído...): no hay grado intermedio.
 * - {@code vetoSiValorSupera}: un límite duro adicional sobre el valor
 *   crudo, independiente de cómo se gradúa la variable. Para variables que
 *   sí necesitan un rango de puntuación parcial (peor_tablespace_pct entre
 *   75-95%) pero además tienen un límite absoluto más allá del cual ya no
 *   importa el promedio (98%: detención inminente).
 */
public record Umbral(
        String variable,
        TipoUmbral tipo,
        double valorOk,
        double valorCritico,
        double puntosPorEvento,
        double pesoEnComponente,
        boolean vetoAbsoluto,
        Optional<Double> vetoSiValorSupera) {

    public static Umbral lineal(String variable, TipoUmbral tipo, double ok, double critico, double peso) {
        return new Umbral(variable, tipo, ok, critico, 0, peso, false, Optional.empty());
    }

    public static Umbral penalizacion(String variable, double puntosPorEvento, double peso) {
        return new Umbral(variable, TipoUmbral.PENALIZACION_DISCRETA, 0, 0, puntosPorEvento, peso, false,
            Optional.empty());
    }

    /** Una sola ocurrencia ya es inaceptable (datafile offline, proceso mandatorio
     * caído...) -- veta TODO el componente, no solo esta variable dentro del promedio. */
    public static Umbral criticoSiHayAlguno(String variable, double peso) {
        return new Umbral(variable, TipoUmbral.CRITICO_SI_HAY_ALGUNO, 0, 0, 0, peso, true, Optional.empty());
    }

    /**
     * Añade un límite duro adicional sobre esta misma variable (vetoSiValorSupera),
     * independiente de vetoAbsoluto -- a propósito NO toca ese campo: vetoAbsoluto
     * comparar contra el propio score graduado dispararía ya en valorCritico (95),
     * no en el umbral de veto real (p. ej. 98), que es más alto a propósito.
     */
    public Umbral conVetoSiSupera(double umbral) {
        return new Umbral(variable, tipo, valorOk, valorCritico, puntosPorEvento, pesoEnComponente, vetoAbsoluto,
            Optional.of(umbral));
    }
}
