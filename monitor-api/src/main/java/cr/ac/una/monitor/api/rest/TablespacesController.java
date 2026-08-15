package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.TablespaceDto;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioTablespaces;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lee directamente RepositorioTablespaces (puerto/salida) -- ver ComponentesController sobre por qué. */
@RestController
@RequestMapping("/api/v1/instancias/{id}/tablespaces")
public class TablespacesController {

    private final RepositorioTablespaces tablespaces;

    public TablespacesController(RepositorioTablespaces tablespaces) {
        this.tablespaces = tablespaces;
    }

    @GetMapping
    public List<TablespaceDto> listar(@PathVariable long id) {
        return tablespaces.ultimo(new InstanciaId(id)).stream()
            .map(TablespaceDto::desde)
            .toList();
    }
}
