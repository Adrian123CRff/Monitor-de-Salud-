package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarSalud;
import cr.ac.una.monitor.aplicacion.puerto.entrada.SaludNoDisponibleException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.springframework.stereotype.Service;

/** Lee el último Isbd calculado (RepositorioIndices) sin disparar un muestreo nuevo. */
@Service
public class ConsultarSaludServicio implements ConsultarSalud {

    private final RepositorioIndices indices;

    public ConsultarSaludServicio(RepositorioIndices indices) {
        this.indices = indices;
    }

    @Override
    public Isbd actual(InstanciaId instancia) {
        return indices.ultimo(instancia).orElseThrow(() -> new SaludNoDisponibleException(instancia));
    }
}
