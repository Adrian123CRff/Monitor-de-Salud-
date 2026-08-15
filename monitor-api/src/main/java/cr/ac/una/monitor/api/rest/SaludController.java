package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.IsbdDto;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarHistorico;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarSalud;
import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final ConsultarSalud consultarSalud;
    private final ConsultarHistorico consultarHistorico;
    private final MuestrearInstancia muestrearInstancia;

    public SaludController(ConsultarSalud consultarSalud, ConsultarHistorico consultarHistorico,
            MuestrearInstancia muestrearInstancia) {
        this.consultarSalud = consultarSalud;
        this.consultarHistorico = consultarHistorico;
        this.muestrearInstancia = muestrearInstancia;
    }

    @GetMapping("/salud")
    public IsbdDto salud(@PathVariable long id) {
        return IsbdDto.desde(consultarSalud.actual(new InstanciaId(id)));
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
