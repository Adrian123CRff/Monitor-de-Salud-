package cr.ac.una.monitor.dominio.calibracion;

import cr.ac.una.monitor.dominio.modelo.Componente;

/**
 * Conjunto de umbrales que se evalúa junto para producir UN Indicador.
 *
 * No coincide con Componente: PROCESOS se calcula en dos pasadas separadas
 * (IP_usuarios e IP_fondo, ver ADR 0006) que después combina
 * CombinadorSubIndicadores. Este enum es la clave real por la que
 * MuestrearInstanciaServicio pide umbrales -- una por cada llamada a
 * CalculadorComponente.calcular().
 */
public enum GrupoUmbral {

    /** Procesos y sesiones de usuario (V$SESSION TYPE='USER'). */
    PROCESOS_USUARIOS(Componente.PROCESOS),

    /** Procesos de fondo mandatorios: DBW0, LGWR, CKPT, PMON, SMON (ADR 0006). */
    PROCESOS_FONDO(Componente.PROCESOS),

    MEMORIA(Componente.MEMORIA),

    ARCHIVOS(Componente.ARCHIVOS);

    private final Componente componente;

    GrupoUmbral(Componente componente) {
        this.componente = componente;
    }

    /** A qué Componente contribuye este grupo (dos grupos comparten PROCESOS). */
    public Componente componente() {
        return componente;
    }
}
