package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.AlertaDto;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioAlertas;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * GET sin parámetros: solo las abiertas, ordenadas por severidad y luego
 * duración (ver JdbcRepositorioAlertas.abiertas()). Con desde/hasta: los
 * episodios que se solapan con esa ventana, abiertos o ya cerrados, para que
 * el gráfico de evolución pueda marcar CUÁNDO dolió y no solo qué duele ahora.
 *
 * Lee directamente RepositorioAlertas (puerto/salida), ver ComponentesController
 * sobre por qué.
 */
@RestController
@RequestMapping("/api/v1/instancias/{id}/alertas")
public class AlertasController {

    private final RepositorioAlertas alertas;

    public AlertasController(RepositorioAlertas alertas) {
        this.alertas = alertas;
    }

    @GetMapping
    public List<AlertaDto> listar(@PathVariable long id,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta) {
        InstanciaId instancia = new InstanciaId(id);
        // Los dos o ninguno: pedir solo uno de los extremos es un rango a medias,
        // y adivinar el que falta produciría una ventana que nadie pidió.
        if ((desde == null) != (hasta == null)) {
            throw new IllegalArgumentException(
                "desde y hasta van juntos: mandá los dos para un rango, o ninguno para las alertas abiertas.");
        }
        List<Alerta> encontradas = desde == null
            ? alertas.abiertas(instancia)
            : alertas.enRango(instancia, desde, hasta);
        return encontradas.stream().map(AlertaDto::desde).toList();
    }
}
