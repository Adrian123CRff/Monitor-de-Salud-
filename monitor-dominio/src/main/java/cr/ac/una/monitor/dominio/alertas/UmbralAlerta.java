package cr.ac.una.monitor.dominio.alertas;

/**
 * Umbrales de severidad para UNA variable, con histéresis (ver skill
 * diseno-de-indicadores / references/calibracion.md, "Parte 2"): entrada y
 * salida distintas por nivel para que una métrica oscilando alrededor de un
 * único corte no dispare alertas por muestra. Deliberadamente distinto de
 * calibracion.Umbral (que normaliza a una puntuación 0-100 continua): esto
 * clasifica en niveles discretos de severidad para el ciclo de vida de
 * alertas, no para el ISBD.
 *
 * Para una variable binaria/grave que debe disparar sin banda intermedia
 * (p. ej. datafile offline), los tres pares entrada/salida pueden colapsar
 * al mismo punto (entradaAdvertencia == entradaAlto == entradaCritico): la
 * cascada de EvaluadorNivel salta directo a CRITICO, sin pasar por
 * ADVERTENCIA/ALTO.
 *
 * confirmacionesRequeridas/ventanaConfirmacion (0/0 = sin confirmación,
 * dispara a la primera muestra): variables binarias y graves no llevan
 * confirmación, es para variables continuas y ruidosas (ver
 * ConfirmadorTemporal).
 */
public record UmbralAlerta(
        String variable,
        double entradaAdvertencia, double salidaAdvertencia,
        double entradaAlto, double salidaAlto,
        double entradaCritico, double salidaCritico,
        int confirmacionesRequeridas, int ventanaConfirmacion) {

    public UmbralAlerta {
        if (!(salidaAdvertencia <= entradaAdvertencia
                && entradaAdvertencia <= entradaAlto
                && salidaAlto <= entradaAlto
                && entradaAlto <= entradaCritico
                && salidaCritico <= entradaCritico)) {
            throw new IllegalArgumentException(
                "Umbrales de alerta inconsistentes para " + variable + ": se espera "
                + "salidaAdvertencia <= entradaAdvertencia <= entradaAlto y salidaAlto <= entradaAlto <= "
                + "entradaCritico y salidaCritico <= entradaCritico.");
        }
        if (confirmacionesRequeridas > ventanaConfirmacion) {
            throw new IllegalArgumentException(
                "confirmacionesRequeridas no puede ser mayor que ventanaConfirmacion para " + variable);
        }
    }

    /** Sin confirmación temporal: dispara a la primera muestra que cruce el umbral. */
    public static UmbralAlerta sinConfirmacion(String variable,
            double entradaAdvertencia, double salidaAdvertencia,
            double entradaAlto, double salidaAlto,
            double entradaCritico, double salidaCritico) {
        return new UmbralAlerta(variable, entradaAdvertencia, salidaAdvertencia,
            entradaAlto, salidaAlto, entradaCritico, salidaCritico, 0, 0);
    }

    /** Binaria/grave: un único punto de corte, sin banda intermedia ni confirmación. */
    public static UmbralAlerta binaria(String variable, double umbral) {
        return new UmbralAlerta(variable, umbral, umbral, umbral, umbral, umbral, umbral, 0, 0);
    }
}
