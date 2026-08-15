package cr.ac.una.monitor.dominio.alertas;

/**
 * Umbral doble (histéresis): el corte de entrada y el de salida son
 * distintos, así que la zona intermedia mantiene el nivel anterior en vez de
 * conmutar en cada muestra (ver skill diseno-de-indicadores /
 * references/calibracion.md, "Mecanismo 1: umbral doble" -- el termostato).
 *
 * evaluar() es una función pura de un solo paso: sube o baja tantos
 * escalones como el valor sostenga dentro de la misma llamada (una caída
 * brusca de CRITICO a NORMAL en una sola muestra no se queda "atascada" en
 * ALTO esperando la próxima).
 */
public final class EvaluadorNivel {

    private EvaluadorNivel() {
    }

    public static Nivel evaluar(double valor, Nivel actual, UmbralAlerta u) {
        Nivel nivel = actual;

        if (nivel != Nivel.CRITICO && valor >= u.entradaCritico()) {
            nivel = Nivel.CRITICO;
        }
        if ((nivel == Nivel.NORMAL || nivel == Nivel.ADVERTENCIA) && valor >= u.entradaAlto()) {
            nivel = Nivel.ALTO;
        }
        if (nivel == Nivel.NORMAL && valor >= u.entradaAdvertencia()) {
            nivel = Nivel.ADVERTENCIA;
        }

        if (nivel == Nivel.CRITICO && valor < u.salidaCritico()) {
            nivel = Nivel.ALTO;
        }
        if (nivel == Nivel.ALTO && valor < u.salidaAlto()) {
            nivel = Nivel.ADVERTENCIA;
        }
        if (nivel == Nivel.ADVERTENCIA && valor < u.salidaAdvertencia()) {
            nivel = Nivel.NORMAL;
        }

        return nivel;
    }
}
