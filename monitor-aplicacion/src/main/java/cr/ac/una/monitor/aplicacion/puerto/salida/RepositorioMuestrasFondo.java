package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

import java.util.Optional;

/**
 * Persistencia del histórico crudo de procesos de fondo (ADR 0002),
 * separada de RepositorioMuestras: Componente.PROCESOS es compartido por
 * las muestras de usuarios y de fondo (decisión deliberada, ver ADR 0006),
 * así que ultima(instancia, Componente.PROCESOS) no podría distinguir cuál
 * de las dos se pidió. Este puerto evita esa ambigüedad con su propia tabla.
 */
public interface RepositorioMuestrasFondo {

    void guardar(InstanciaId instancia, Muestra muestra);

    /** Necesaria para calcular deltas de contadores acumulados (b2/b3/b4). */
    Optional<Muestra> ultima(InstanciaId instancia);
}
