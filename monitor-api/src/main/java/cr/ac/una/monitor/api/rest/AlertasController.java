package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.AlertaDto;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioAlertas;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solo alertas abiertas, ya ordenadas por severidad y luego duración (ver
 * JdbcRepositorioAlertas.abiertas()) -- lee directamente RepositorioAlertas
 * (puerto/salida), ver ComponentesController sobre por qué.
 */
@RestController
@RequestMapping("/api/v1/instancias/{id}/alertas")
public class AlertasController {

    private final RepositorioAlertas alertas;

    public AlertasController(RepositorioAlertas alertas) {
        this.alertas = alertas;
    }

    @GetMapping
    public List<AlertaDto> abiertas(@PathVariable long id) {
        return alertas.abiertas(new InstanciaId(id)).stream()
            .map(AlertaDto::desde)
            .toList();
    }
}
