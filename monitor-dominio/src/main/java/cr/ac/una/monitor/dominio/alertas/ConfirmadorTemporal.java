package cr.ac.una.monitor.dominio.alertas;

import java.util.List;

/**
 * "N de las últimas M" en vez de "N consecutivas" (ver skill
 * diseno-de-indicadores / references/calibracion.md, "Mecanismo 2:
 * confirmación temporal"): más robusto porque tolera una lectura anómala en
 * medio de una condición real, un caso frecuente. El costo es latencia --
 * con confirmación de 3 y muestreo cada 60s la alerta llega ~3 min tarde,
 * por eso las variables binarias/graves usan UmbralAlerta.binaria() (0
 * confirmaciones requeridas) en vez de este mecanismo.
 *
 * Pura a propósito: no sabe de dónde salen las "últimas" -- quien la llama
 * arma la lista (MuestrearInstanciaServicio, con RepositorioMuestras.ultimasN;
 * ver AlertasIniciales.sesionesBloqueadas()/presionPga() para las dos
 * variables que la usan, "2 de 3" y "3 de 5" según la tabla de la skill).
 * Solo gatea el disparo inicial (NORMAL -> algo distinto de NORMAL); una vez
 * abierto el episodio, EvaluadorNivel reacciona sin confirmación adicional.
 */
public final class ConfirmadorTemporal {

    private ConfirmadorTemporal() {
    }

    /**
     * @param ultimasCruzoPrimero si el umbral se cruzó en cada una de las últimas muestras,
     *                            la más reciente primero.
     */
    public static boolean confirmada(List<Boolean> ultimasCruzoPrimero, int requeridas, int ventana) {
        return ultimasCruzoPrimero.stream()
            .limit(ventana)
            .filter(Boolean::booleanValue)
            .count() >= requeridas;
    }
}
