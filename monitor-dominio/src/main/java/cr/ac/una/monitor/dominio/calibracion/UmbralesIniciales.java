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
 * reales -- pendiente P4 / calibración del registro de decisiones.
 *
 * PENDIENTE: no cubre todavía p7 (contexto, correcto no incluirlo), m1-m6
 * (contexto), a1/a3/a5/a6 (contexto o sin normalizar aún), ni la subdivisión
 * IP_usuarios / IP_fondo (P4).
 */
public final class UmbralesIniciales {

    private UmbralesIniciales() {
    }

    public static List<Umbral> procesos() {
        return List.of(
            Umbral.lineal("util_procesos_pct", LINEAL_INVERTIDA, 70, 95, 0.25),
            Umbral.lineal("util_sesiones_pct", LINEAL_INVERTIDA, 70, 95, 0.25),
            Umbral.penalizacion("p6_sesiones_bloqueadas", 25, 0.25),
            Umbral.lineal("bloqueo_max_seg", LINEAL_INVERTIDA, 5, 120, 0.25));
    }

    public static List<Umbral> memoria() {
        return List.of(
            Umbral.lineal("pga_uso_pct", LINEAL_INVERTIDA, 90, 130, 0.4),
            Umbral.penalizacion("over_alloc_delta", 20, 0.3),
            Umbral.lineal("cache_hit_pct_delta", LINEAL_DIRECTA, 90, 50, 0.3));
    }

    public static List<Umbral> archivos() {
        return List.of(
            Umbral.lineal("peor_tablespace_pct", LINEAL_INVERTIDA, 75, 95, 0.4),
            Umbral.criticoSiHayAlguno("a2_datafiles_offline", 0.2),
            Umbral.criticoSiHayAlguno("a7_archivos_invalidos", 0.2),
            Umbral.criticoSiHayAlguno("a8_archivos_recover", 0.1),
            Umbral.lineal("redundancia_redo", LINEAL_DIRECTA, 2, 1, 0.1));
    }
}
