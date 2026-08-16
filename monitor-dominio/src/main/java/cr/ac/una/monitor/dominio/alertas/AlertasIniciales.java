package cr.ac.una.monitor.dominio.alertas;

/**
 * Umbrales de alerta de arranque (NO calibrados, igual que UmbralesIniciales
 * en calibracion) -- cinco variables que cubren los tres mecanismos de la
 * skill (histéresis sola, sin mecanismo, e histéresis + confirmación
 * temporal, ver ConfirmadorTemporal).
 *
 * - a2_datafiles_offline: binaria y grave (ver tabla "Qué mecanismo para
 *   qué variable" de la skill) -- UmbralAlerta.binaria(), dispara a la
 *   primera muestra, sin banda intermedia.
 * - b1_procesos_caidos: binaria y grave, igual que datafiles_offline (ver
 *   procesosCaidos() más abajo sobre por qué hace falta pese al veto
 *   absoluto que ya aplica MotorIndicadores sobre IP_fondo).
 * - peor_tablespace_pct (por tablespace, entidad = nombre): histéresis
 *   amplia, la variable cambia lento y no necesita reactividad. Los cortes
 *   (75/90/98) son los mismos que UmbralesIniciales.archivos() usa para
 *   puntuar y el veto absoluto de ADR ("tablespace >= 98%") -- misma fuente
 *   de umbral, dos usos distintos (puntuación continua vs. episodio de
 *   alerta), consistencia deliberada.
 * - p6_sesiones_bloqueadas: confirmación 2 de 3 -- ruidosa (una sesión
 *   bloqueada un instante es común y se resuelve sola) pero hay que
 *   reaccionar rápido una vez confirmada, por eso la ventana es corta.
 * - m8_over_alloc_delta: confirmación 3 de 5 -- "un evento aislado es
 *   ruido, un patrón sostenido no" (misma tabla de la skill). Se evalúa
 *   sobre la delta del intervalo (MuestrearInstanciaServicio.conDeltas()),
 *   nunca sobre el acumulado.
 *
 * Descartada por ahora: utilización de procesos/sesiones (candidata en el
 * plan de trabajo junto a b1). La skill la marca "EWMA α=0.3 + histéresis"
 * -- un tercer mecanismo (suavizado exponencial) que este proyecto no
 * implementa todavía, y que además necesitaría datos reales de línea base
 * (B1) para elegir un umbral defendible; calibrarla a ciegas, sin esos
 * datos, sería peor que no tenerla. b1_procesos_caidos, en cambio, reutiliza
 * el mismo mecanismo (binaria, sin calibrar nada) que datafiles_offline.
 */
public final class AlertasIniciales {

    private AlertasIniciales() {
    }

    public static UmbralAlerta datafilesOffline() {
        return UmbralAlerta.binaria("a2_datafiles_offline", 1);
    }

    /**
     * El veto absoluto de MotorIndicadores sobre IP_fondo (ver
     * MuestrearInstanciaServicioTest.FONDO_CRITICO) fuerza el ISBD a
     * CRITICO cuando un proceso de fondo mandatorio (DBW0/LGWR/CKPT/PMON/
     * SMON) está caído, pero eso es una foto del ciclo actual: no dice
     * desde cuándo, ni queda en MONITOR_ALERTAS para el historial del
     * panel de alertas. Una alerta aparte (mismo patrón que
     * datafilesOffline(): binaria, dispara de inmediato) le da a esa
     * condición el ciclo de vida con apertura/cierre que el ISBD por sí
     * solo no registra.
     */
    public static UmbralAlerta procesosCaidos() {
        return UmbralAlerta.binaria("b1_procesos_caidos", 1);
    }

    public static UmbralAlerta peorTablespacePct() {
        return UmbralAlerta.sinConfirmacion("peor_tablespace_pct",
            75, 70,   // ADVERTENCIA: entra a 75%, sale a 70%
            90, 85,   // ALTO: entra a 90%, sale a 85%
            98, 95);  // CRITICO: entra a 98% (mismo límite duro del veto absoluto), sale a 95%
    }

    public static UmbralAlerta sesionesBloqueadas() {
        return UmbralAlerta.conConfirmacion("p6_sesiones_bloqueadas",
            1, 0,   // ADVERTENCIA: entra con 1 sesión bloqueada confirmada, sale al despejar del todo
            3, 2,   // ALTO: entra a 3, sale a 2
            5, 4,   // CRITICO: entra a 5, sale a 4
            2, 3);  // confirmación 2 de 3
    }

    public static UmbralAlerta presionPga() {
        return UmbralAlerta.conConfirmacion("m8_over_alloc_delta",
            1, 0,     // ADVERTENCIA: entra con cualquier evento nuevo confirmado de sobreasignación
            5, 3,     // ALTO
            15, 10,   // CRITICO
            3, 5);    // confirmación 3 de 5
    }
}
