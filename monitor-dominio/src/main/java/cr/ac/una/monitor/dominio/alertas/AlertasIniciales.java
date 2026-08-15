package cr.ac.una.monitor.dominio.alertas;

/**
 * Umbrales de alerta de arranque (NO calibrados, igual que UmbralesIniciales
 * en calibracion) para el primer conjunto de variables con alertas: las dos
 * que mejor representan los dos mecanismos de la skill sin necesitar
 * confirmación temporal todavía (ver ConfirmadorTemporal sobre por qué no
 * está conectada aún).
 *
 * - a2_datafiles_offline: binaria y grave (ver tabla "Qué mecanismo para
 *   qué variable" de la skill) -- UmbralAlerta.binaria(), dispara a la
 *   primera muestra, sin banda intermedia.
 * - peor_tablespace_pct (por tablespace, entidad = nombre): histéresis
 *   amplia, la variable cambia lento y no necesita reactividad. Los cortes
 *   (75/90/98) son los mismos que UmbralesIniciales.archivos() usa para
 *   puntuar y el veto absoluto de ADR ("tablespace >= 98%") -- misma fuente
 *   de umbral, dos usos distintos (puntuación continua vs. episodio de
 *   alerta), consistencia deliberada.
 *
 * PENDIENTE: sesiones bloqueadas (confirmación 2 de 3 según la misma tabla)
 * y presión de PGA (confirmación 3 de 5) quedan para cuando se conecte
 * ConfirmadorTemporal al orquestador.
 */
public final class AlertasIniciales {

    private AlertasIniciales() {
    }

    public static UmbralAlerta datafilesOffline() {
        return UmbralAlerta.binaria("a2_datafiles_offline", 1);
    }

    public static UmbralAlerta peorTablespacePct() {
        return UmbralAlerta.sinConfirmacion("peor_tablespace_pct",
            75, 70,   // ADVERTENCIA: entra a 75%, sale a 70%
            90, 85,   // ALTO: entra a 90%, sale a 85%
            98, 95);  // CRITICO: entra a 98% (mismo límite duro del veto absoluto), sale a 95%
    }
}
