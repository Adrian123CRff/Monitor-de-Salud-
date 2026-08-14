package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.util.List;

/** Persistencia de MONITOR_ALERTAS (abrir, cerrar, listar abiertas). Tipo Alerta: pendiente en dominio.alertas. */
public interface RepositorioAlertas {

    List<Object> abiertas(InstanciaId instancia);
}
