package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.IsbdDto;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarHistorico;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarSalud;
import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * GET /salud lee el último Isbd calculado (RepositorioIndices, vía
 * ConsultarSalud) sin tocar Oracle -- para eso está POST /muestrear, que
 * dispara un ciclo real (útil también para forzar un cambio visible en
 * una demo en vivo, ver skill de arquitectura).
 */
@RestController
@RequestMapping("/api/v1/instancias/{id}")
public class SaludController {

    /**
     * Encontrado por auditoría externa (ver docs/, IsbdDto.vetusto): si el
     * planificador lleva varios ciclos sin poder correr, /salud sigue
     * devolviendo el último Isbd bueno sin avisar que ya no es reciente.
     * 3x el intervalo de muestreo (mismo margen que CalculadorDelta usa
     * para "ciclo perdido") evita marcar vetusto por un solo ciclo lento.
     */
    private static final int MULTIPLICADOR_VETUSTEZ = 3;

    private final ConsultarSalud consultarSalud;
    private final ConsultarHistorico consultarHistorico;
    private final MuestrearInstancia muestrearInstancia;
    private final Duration intervaloMuestreo;

    public SaludController(ConsultarSalud consultarSalud, ConsultarHistorico consultarHistorico,
            MuestrearInstancia muestrearInstancia,
            @Value("${monitor.muestreo.intervalo}") String intervaloMuestreo) {
        this.consultarSalud = consultarSalud;
        this.consultarHistorico = consultarHistorico;
        this.muestrearInstancia = muestrearInstancia;
        // Duration.parse en vez de dejar que @Value convierta directo a Duration:
        // la conversión automática de ISO-8601 en @Value depende de que
        // ApplicationConversionService quede instalada como ConversionService
        // por defecto del bean factory, que no siempre está garantizado --
        // parsear a mano es explícito y no depende de ese detalle de Boot.
        this.intervaloMuestreo = Duration.parse(intervaloMuestreo);
    }

    @GetMapping("/salud")
    public IsbdDto salud(@PathVariable long id) {
        Isbd isbd = consultarSalud.actual(new InstanciaId(id));
        Duration umbral = intervaloMuestreo.multipliedBy(MULTIPLICADOR_VETUSTEZ);
        boolean vetusto = Duration.between(isbd.momento(), Instant.now()).compareTo(umbral) > 0;
        return IsbdDto.desde(isbd, vetusto);
    }

    @GetMapping("/salud/historico")
    public List<IsbdDto> historico(@PathVariable long id, @RequestParam Instant desde, @RequestParam Instant hasta) {
        return consultarHistorico.enRango(new InstanciaId(id), desde, hasta).stream()
            .map(IsbdDto::desde)
            .toList();
    }

    @PostMapping("/muestrear")
    public IsbdDto muestrear(@PathVariable long id) {
        return IsbdDto.desde(muestrearInstancia.ejecutar(new InstanciaId(id)));
    }
}
