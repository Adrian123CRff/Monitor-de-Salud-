package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.time.Instant;
import java.util.List;

/**
 * Persistencia del histórico crudo de detalle por tablespace (MONITOR_TABLESPACE,
 * ADR 0002). Sin "ultima"/enRango todavía -- a diferencia de over allocation count
 * o los contadores de fondo, used_percent es un valor instantáneo, no un acumulado
 * que requiera delta; nadie necesita historial de esto todavía tampoco (igual que
 * enRango() en RepositorioMuestras, se implementa cuando haga falta).
 */
public interface RepositorioTablespaces {

    void guardar(InstanciaId instancia, Instant momento, List<DetalleTablespace> detalle);
}
