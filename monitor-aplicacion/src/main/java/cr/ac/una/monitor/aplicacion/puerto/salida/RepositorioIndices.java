package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia del histórico de ISBD calculados (MONITOR_INDICES, ADR 0002).
 * Existía la tabla desde V1 pero nada la usaba -- ConsultarSalud/ConsultarHistorico
 * (puerto/entrada) no tenían de dónde leer. Se llena desde MuestrearInstanciaServicio
 * cada vez que calcula un Isbd, sea desde el planificador o desde POST /muestrear.
 */
public interface RepositorioIndices {

    void guardar(InstanciaId instancia, Isbd isbd);

    /** Necesario para ConsultarSalud: el último calculado, sin forzar un muestreo nuevo. */
    Optional<Isbd> ultimo(InstanciaId instancia);

    List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta);
}
