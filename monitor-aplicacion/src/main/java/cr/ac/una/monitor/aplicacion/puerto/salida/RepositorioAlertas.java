package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de MONITOR_ALERTAS (ADR 0002): episodios con apertura y
 * cierre (ver MotorAlertas), no eventos puntuales.
 */
public interface RepositorioAlertas {

    /** Clave de deduplicación: (instancia, variable, entidad) -- ver Alerta. */
    Optional<Alerta> buscarAbierta(InstanciaId instancia, String variable, Optional<String> entidad);

    /** Devuelve la Alerta con el id ya asignado por la base. */
    Alerta abrir(Alerta nueva);

    void cerrar(Alerta existente, Instant cerradaEn);

    List<Alerta> abiertas(InstanciaId instancia);
}
