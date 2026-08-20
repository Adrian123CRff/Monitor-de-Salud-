package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.ResumenInstanciaDto;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ListarInstancias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * GET /instancias -- la vista general (pedido del profesor: un dashboard
 * principal con todas las bases de datos, cada una con su semáforo; clic en
 * un tile entra al dashboard de detalle que ya existe, ver SaludController).
 */
@RestController
@RequestMapping("/api/v1/instancias")
public class InstanciasController {

    private final ListarInstancias listarInstancias;
    private final Duration intervaloMuestreo;

    public InstanciasController(ListarInstancias listarInstancias,
            @Value("${monitor.muestreo.intervalo}") String intervaloMuestreo) {
        this.listarInstancias = listarInstancias;
        this.intervaloMuestreo = Duration.parse(intervaloMuestreo);
    }

    @GetMapping
    public List<ResumenInstanciaDto> listar() {
        return listarInstancias.listar().stream()
            .map(resumen -> ResumenInstanciaDto.desde(resumen, intervaloMuestreo))
            .toList();
    }
}
