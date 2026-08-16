package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComponentesController.class)
class ComponentesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioMuestras muestras;

    @MockitoBean
    private RepositorioMuestrasFondo muestrasFondo;

    @Test
    void get_procesos_combina_usuarios_y_fondo_bajo_claves_distintas() throws Exception {
        InstanciaId instancia = new InstanciaId(1L);
        Muestra usuarios = new Muestra(Componente.PROCESOS, Instant.parse("2026-08-16T10:00:00Z"),
            Map.of("p1_sesiones_activas", 12.0), false);
        Muestra fondo = new Muestra(Componente.PROCESOS, Instant.parse("2026-08-16T10:00:00Z"),
            Map.of("b2_jobs_fallidos", 0.0), false);
        when(muestras.ultima(instancia, Componente.PROCESOS)).thenReturn(Optional.of(usuarios));
        when(muestrasFondo.ultima(instancia)).thenReturn(Optional.of(fondo));

        mvc.perform(get("/api/v1/instancias/1/componentes/PROCESOS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarios.valores.p1_sesiones_activas").value(12.0))
            .andExpect(jsonPath("$.fondo.valores.b2_jobs_fallidos").value(0.0));
    }

    @Test
    void get_memoria_no_incluye_fondo() throws Exception {
        InstanciaId instancia = new InstanciaId(1L);
        Muestra actual = new Muestra(Componente.MEMORIA, Instant.parse("2026-08-16T10:00:00Z"),
            Map.of("m1_sga_used_pct", 55.0), false);
        when(muestras.ultima(instancia, Componente.MEMORIA)).thenReturn(Optional.of(actual));

        mvc.perform(get("/api/v1/instancias/1/componentes/MEMORIA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actual.valores.m1_sga_used_pct").value(55.0))
            .andExpect(jsonPath("$.fondo").doesNotExist());
    }

    @Test
    void get_sin_muestras_todavia_devuelve_404() throws Exception {
        when(muestras.ultima(new InstanciaId(1L), Componente.ARCHIVOS)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/instancias/1/componentes/ARCHIVOS"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void get_componente_invalido_devuelve_400() throws Exception {
        mvc.perform(get("/api/v1/instancias/1/componentes/NO_EXISTE"))
            .andExpect(status().isBadRequest());
    }
}
