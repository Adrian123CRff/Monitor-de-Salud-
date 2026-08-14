package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistencia del histórico crudo, en la base de datos separada del ADR 0002. */
public interface RepositorioMuestras {

    void guardar(InstanciaId instancia, Muestra muestra);

    /** Necesaria para calcular deltas de contadores acumulados (p. ej. over allocation count). */
    Optional<Muestra> ultima(InstanciaId instancia, Componente componente);

    List<Muestra> enRango(InstanciaId instancia, Componente componente, Instant desde, Instant hasta);
}
