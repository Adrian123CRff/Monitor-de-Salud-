package cr.ac.una.monitor.dominio.calibracion;

/**
 * Tamaño de la instancia monitoreada, que decide qué juego de umbrales se le
 * aplica. Responde al requisito de clase de que el monitor sea *paramétrico*:
 * "una base de datos pequeña no tiene los mismos umbrales que una grande".
 *
 * ESTANDAR es la línea base y el respaldo: son los valores de diseño
 * (UmbralesIniciales), los únicos que hoy están sembrados. Los otros tres
 * perfiles existen como estructura, pero deliberadamente NO traen valores
 * inventados -- diferenciarlos es justo el resultado que debe producir la
 * calibración con datos reales (Módulo B del plan de trabajo). Hasta
 * entonces, una instancia marcada PEQUENA se comporta igual que una
 * ESTANDAR.
 *
 * El respaldo es POR VARIABLE, no por perfil completo (ver
 * RepositorioUmbrales): un perfil puede redefinir solo las variables que de
 * verdad cambian con el tamaño (util_procesos_pct, peor_tablespace_pct) y
 * heredar de ESTANDAR las que no (a2_datafiles_offline es igual de grave en
 * cualquier base).
 */
public enum PerfilInstancia {

    /** Línea base: los valores de diseño. Siempre existe, siempre completa. */
    ESTANDAR,

    PEQUENA,
    MEDIANA,
    GRANDE;

    /** Tolera nulo y mayúsculas/minúsculas; cualquier valor desconocido cae en ESTANDAR. */
    public static PerfilInstancia desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return ESTANDAR;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ESTANDAR;
        }
    }
}
