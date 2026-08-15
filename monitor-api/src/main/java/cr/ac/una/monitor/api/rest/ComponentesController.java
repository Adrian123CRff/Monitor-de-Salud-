package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.api.dto.MuestraDto;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee directamente RepositorioMuestras/RepositorioMuestrasFondo (puerto/salida),
 * sin un caso de uso intermedio -- simplificación deliberada para una lectura
 * plana de "lo último guardado", sin lógica de negocio propia. La regla de
 * dependencia (ArchUnit) no lo prohíbe: solo veta depender de
 * infraestructura.persistencia (los adaptadores concretos), no de los puertos.
 *
 * Para PROCESOS, la respuesta trae "usuarios" y "fondo" por separado --
 * Componente.PROCESOS es ambiguo entre las dos fuentes (ver ADR 0006 /
 * RepositorioMuestrasFondo), no hay una única "última muestra de PROCESOS".
 */
@RestController
@RequestMapping("/api/v1/instancias/{id}/componentes")
public class ComponentesController {

    private final RepositorioMuestras muestras;
    private final RepositorioMuestrasFondo muestrasFondo;

    public ComponentesController(RepositorioMuestras muestras, RepositorioMuestrasFondo muestrasFondo) {
        this.muestras = muestras;
        this.muestrasFondo = muestrasFondo;
    }

    @GetMapping("/{componente}")
    public Map<String, MuestraDto> detalle(@PathVariable long id, @PathVariable String componente) {
        InstanciaId instancia = new InstanciaId(id);
        Componente c = Componente.valueOf(componente.toUpperCase());

        Map<String, MuestraDto> resultado = new LinkedHashMap<>();
        String claveAgregado = c == Componente.PROCESOS ? "usuarios" : "actual";
        muestras.ultima(instancia, c).ifPresent(m -> resultado.put(claveAgregado, MuestraDto.desde(m)));
        if (c == Componente.PROCESOS) {
            muestrasFondo.ultima(instancia).ifPresent(m -> resultado.put("fondo", MuestraDto.desde(m)));
        }

        if (resultado.isEmpty()) {
            throw new SinDatosException(instancia, c);
        }
        return resultado;
    }
}
