package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.CalibracionDto;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lee/escribe directamente RepositorioCalibracion (puerto/salida) -- ver ComponentesController sobre por qué. */
@RestController
@RequestMapping("/api/v1/calibracion")
public class CalibracionController {

    private final RepositorioCalibracion calibraciones;

    public CalibracionController(RepositorioCalibracion calibraciones) {
        this.calibraciones = calibraciones;
    }

    @GetMapping
    public CalibracionDto vigente() {
        Calibracion vigente = calibraciones.vigente();
        return CalibracionDto.desde(vigente != null ? vigente : Calibracion.inicial());
    }

    @PutMapping
    public CalibracionDto registrar(@RequestBody CalibracionDto nueva) {
        Calibracion calibracion = nueva.aDominio();
        calibraciones.registrar(calibracion);
        return CalibracionDto.desde(calibracion);
    }
}
