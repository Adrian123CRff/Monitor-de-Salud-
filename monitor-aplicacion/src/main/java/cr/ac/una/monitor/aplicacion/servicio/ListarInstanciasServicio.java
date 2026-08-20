package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ListarInstancias;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioInstancias;
import cr.ac.una.monitor.dominio.modelo.Instancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaResumen;
import org.springframework.stereotype.Service;

import java.util.List;

/** Combina el catálogo (RepositorioInstancias) con el último Isbd de cada una (RepositorioIndices). */
@Service
public class ListarInstanciasServicio implements ListarInstancias {

    private final RepositorioInstancias instancias;
    private final RepositorioIndices indices;

    public ListarInstanciasServicio(RepositorioInstancias instancias, RepositorioIndices indices) {
        this.instancias = instancias;
        this.indices = indices;
    }

    @Override
    public List<InstanciaResumen> listar() {
        return instancias.listarActivas().stream()
            .map(this::conSalud)
            .toList();
    }

    private InstanciaResumen conSalud(Instancia instancia) {
        return new InstanciaResumen(instancia, indices.ultimo(instancia.id()));
    }
}
