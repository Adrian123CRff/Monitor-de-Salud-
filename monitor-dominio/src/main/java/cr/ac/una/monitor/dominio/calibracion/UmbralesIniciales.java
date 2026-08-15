package cr.ac.una.monitor.dominio.calibracion;

import java.util.List;

import static cr.ac.una.monitor.dominio.calibracion.TipoUmbral.LINEAL_DIRECTA;
import static cr.ac.una.monitor.dominio.calibracion.TipoUmbral.LINEAL_INVERTIDA;

/**
 * Umbrales de arranque (NO calibrados) para las variables derivadas cubiertas
 * en esta primera versión. Valores tomados de la skill diseno-de-indicadores
 * / references/normalizacion.md ("Asignación por variable en este proyecto").
 *
 * Pesos: distribución equitativa entre las variables que puntúan en cada
 * componente -- el punto de partida que el propio profesor sugirió antes de
 * tener datos para calibrar (notas de clase: "Podemos comenzar con una
 * distribución equitativa..."). La misma skill señala que p6 (sesiones
 * bloqueadas) y peor_tablespace_pct son las variables más valiosas de sus
 * componentes; vale la pena revisar si merecen más peso una vez haya datos
 * reales -- pendiente calibración del registro de decisiones.
 *
 * PENDIENTE: no cubre todavía p7 (contexto, correcto no incluirlo), m1-m6
 * (contexto), a1/a3/a5/a6 (contexto o sin normalizar aún).
 *
 * IP_usuarios / IP_fondo (antes P4, ver ADR 0006): PROCESOS ya no es un
 * único indicador plano -- procesosUsuarios() y procesosFondo() se
 * calculan por separado y se combinan con PESO_IP_USUARIOS/PESO_IP_FONDO
 * (ver CombinadorSubIndicadores).
 */
public final class UmbralesIniciales {

    /** Pesos para combinar IP_usuarios e IP_fondo en el IP final. Ver ADR 0006. */
    public static final double PESO_IP_USUARIOS = 0.40;
    public static final double PESO_IP_FONDO = 0.60;

    private UmbralesIniciales() {
    }

    /**
     * Procesos y sesiones iniciados por usuarios (V$SESSION TYPE='USER').
     * Antes se llamaba simplemente procesos() -- ver ADR 0006.
     */
    public static List<Umbral> procesosUsuarios() {
        return List.of(
            Umbral.lineal("util_procesos_pct", LINEAL_INVERTIDA, 70, 95, 0.25),
            Umbral.lineal("util_sesiones_pct", LINEAL_INVERTIDA, 70, 95, 0.25),
            Umbral.penalizacion("p6_sesiones_bloqueadas", 25, 0.25),
            Umbral.lineal("bloqueo_max_seg", LINEAL_INVERTIDA, 5, 120, 0.25));
    }

    /**
     * Procesos de fondo mandatorios: DBW0 (DBWR), LGWR, CKPT, PMON, SMON
     * (ver ADR 0006 y la transcripción de clase BD13-8). b1 es la variable
     * más severa -- si un proceso mandatorio no está activo, no hay grado
     * intermedio, por eso pesa más que las demás dentro del componente.
     *
     * b2/b3 ya NO son AVERAGE_WAIT acumulado desde el arranque (mismo
     * problema que tenía m9_cache_hit_pct en memoria): MuestrearInstanciaServicio
     * las calcula como delta(time_waited)/delta(total_waits) del intervalo
     * contra la última muestra de fondo guardada (CalculadorDelta), y b4
     * pasa de acumulado a delta del intervalo. Los umbrales (ok=1,
     * critico=10, en centésimas de segundo) no cambian: el promedio del
     * intervalo está en la misma unidad y magnitud que el AVERAGE_WAIT que
     * reemplaza, verificado contra una instancia Oracle viva.
     */
    public static List<Umbral> procesosFondo() {
        return List.of(
            Umbral.criticoSiHayAlguno("b1_procesos_caidos", 0.40),
            Umbral.lineal("b2_lgwr_espera_avg", LINEAL_INVERTIDA, 1, 10, 0.25),
            Umbral.lineal("b3_dbwr_espera_avg", LINEAL_INVERTIDA, 1, 10, 0.20),
            Umbral.penalizacion("b4_ckpt_switch_incompleto", 20, 0.15));
    }

    /**
     * m9_cache_hit_pct NO se puntúa: es un promedio acumulado desde el
     * arranque (no un contador), así que no se le puede sacar una delta
     * real -- la skill oracle-vistas-dinamicas lo advierte explícitamente
     * y sugiere la alternativa que se usa aquí: V$SQL_WORKAREA_HISTOGRAM,
     * que sí es un contador real, verificado contra una instancia Oracle
     * viva antes de usarlo (SUM(multipasses_executions), 0 en una instancia
     * sana e inactiva, como se esperaba).
     *
     * m8_over_alloc_delta y m10_multipass_delta se calculan en
     * MuestrearInstanciaServicio con CalculadorDelta, comparando contra la
     * última muestra guardada -- no son un passthrough del acumulado.
     */
    public static List<Umbral> memoria() {
        return List.of(
            Umbral.lineal("pga_uso_pct", LINEAL_INVERTIDA, 90, 130, 0.4),
            Umbral.penalizacion("m8_over_alloc_delta", 20, 0.3),
            Umbral.penalizacion("m10_multipass_delta", 10, 0.3));
    }

    /**
     * Vetos absolutos (ver skill diseno-de-indicadores / references/agregacion.md,
     * "Parte 2 -- Reglas de veto"): a2/a7/a8 ya vetaban TODO ARCHIVOS a través de
     * criticoSiHayAlguno (no diluyen, ver CalculadorComponente); peor_tablespace_pct
     * suma un límite duro adicional en 98% -- entre 75-95% sigue dando puntuación
     * parcial (detección temprana), pero a partir de 98% ("detención inminente,
     * cuestión de minutos u horas") ya no importa el promedio.
     */
    public static List<Umbral> archivos() {
        return List.of(
            Umbral.lineal("peor_tablespace_pct", LINEAL_INVERTIDA, 75, 95, 0.4).conVetoSiSupera(98),
            Umbral.criticoSiHayAlguno("a2_datafiles_offline", 0.2),
            Umbral.criticoSiHayAlguno("a7_archivos_invalidos", 0.2),
            Umbral.criticoSiHayAlguno("a8_archivos_recover", 0.1),
            Umbral.lineal("redundancia_redo", LINEAL_DIRECTA, 2, 1, 0.1));
    }
}
