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

    /**
     * Episodios que se SOLAPAN con la ventana, abiertos o ya cerrados.
     *
     * Solapan, no "abiertos dentro de": un episodio que empezo ayer y sigue
     * abierto afecta a la ventana de hoy y tiene que salir. La condicion es la
     * clasica de interseccion de intervalos -- abrio antes de que la ventana
     * termine, y cerro despues de que empiece (o no ha cerrado).
     *
     * Alimenta las franjas del grafico de evolucion: sin esto el grafico solo
     * podria marcar lo que esta roto AHORA, no cuando dolio.
     */
    List<Alerta> enRango(InstanciaId instancia, Instant desde, Instant hasta);
}
