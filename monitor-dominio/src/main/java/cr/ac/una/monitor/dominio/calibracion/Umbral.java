package cr.ac.una.monitor.dominio.calibracion;

/**
 * Configuración de normalización de una variable derivada (util_procesos_pct,
 * peor_tablespace_pct, etc. -- ver catalogo-variables.md "Variables derivadas").
 * Vive en tabla (monitor_umbral), no en código; estas son valores iniciales
 * de diseño (ver UmbralesIniciales), no resultados de calibración.
 */
public record Umbral(
        String variable,
        TipoUmbral tipo,
        double valorOk,
        double valorCritico,
        double puntosPorEvento,
        double pesoEnComponente) {

    public static Umbral lineal(String variable, TipoUmbral tipo, double ok, double critico, double peso) {
        return new Umbral(variable, tipo, ok, critico, 0, peso);
    }

    public static Umbral penalizacion(String variable, double puntosPorEvento, double peso) {
        return new Umbral(variable, TipoUmbral.PENALIZACION_DISCRETA, 0, 0, puntosPorEvento, peso);
    }

    public static Umbral criticoSiHayAlguno(String variable, double peso) {
        return new Umbral(variable, TipoUmbral.CRITICO_SI_HAY_ALGUNO, 0, 0, 0, peso);
    }
}
