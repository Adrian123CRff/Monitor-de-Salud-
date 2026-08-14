package cr.ac.una.monitor.dominio.calibracion;

/** Qué función de normalización aplica a una variable. Ver skill diseno-de-indicadores. */
public enum TipoUmbral {
    LINEAL_INVERTIDA,
    LINEAL_DIRECTA,
    PENALIZACION_DISCRETA,
    CRITICO_SI_HAY_ALGUNO,
    /** No puntúa: se muestra como dato de contexto, sin peso en el índice. */
    CONTEXTO
}
