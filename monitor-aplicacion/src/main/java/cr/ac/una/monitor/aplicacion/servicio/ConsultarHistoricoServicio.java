package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarHistorico;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Serie temporal de Isbd calculados en un rango (RepositorioIndices). */
@Service
public class ConsultarHistoricoServicio implements ConsultarHistorico {

    private final RepositorioIndices indices;

    public ConsultarHistoricoServicio(RepositorioIndices indices) {
        this.indices = indices;
    }

    @Override
    public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
        return indices.enRango(instancia, desde, hasta);
    }
}
