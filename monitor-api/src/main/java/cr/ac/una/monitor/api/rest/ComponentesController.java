package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.DetalleComponenteDto;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El detalle detrás de un tile de IP/IM/IA: el dato crudo de Oracle más
 * cuánto puntuó cada variable, para poder ver cuál está fuera de límites.
 *
 * Antes este controlador leía RepositorioMuestras directamente, sin caso de
 * uso -- razonable mientras la respuesta era "lo último guardado, tal cual".
 * Al agregar la puntuación aparece una decisión de negocio real (qué
 * umbrales aplicar a esta instancia, cómo ordenar el aporte de cada
 * variable, qué hacer cuando la muestra no se puede puntuar), y eso vive en
 * ConsultarComponenteServicio, no en el borde HTTP.
 *
 * Para PROCESOS la respuesta trae "usuarios" y "fondo" por separado --
 * Componente.PROCESOS es ambiguo entre las dos fuentes (ver ADR 0006).
 */
@RestController
@RequestMapping("/api/v1/instancias/{id}/componentes")
public class ComponentesController {

    private final ConsultarComponente consultarComponente;

    public ComponentesController(ConsultarComponente consultarComponente) {
        this.consultarComponente = consultarComponente;
    }

    @GetMapping("/{componente}")
    public Map<String, DetalleComponenteDto> detalle(@PathVariable long id, @PathVariable String componente) {
        InstanciaId instancia = new InstanciaId(id);
        Componente c = Componente.valueOf(componente.toUpperCase());

        var vistas = consultarComponente.detalle(instancia, c);
        if (vistas.isEmpty()) {
            throw new SinDatosException(instancia, c);
        }

        Map<String, DetalleComponenteDto> resultado = new LinkedHashMap<>();
        vistas.forEach((clave, vista) -> resultado.put(clave, DetalleComponenteDto.desde(vista)));
        return resultado;
    }
}
