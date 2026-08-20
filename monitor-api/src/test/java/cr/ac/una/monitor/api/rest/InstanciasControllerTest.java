package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ListarInstancias;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Instancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.InstanciaResumen;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice de MVC para GET /api/v1/instancias -- la vista general, ver SaludControllerTest para el detalle. */
@WebMvcTest(InstanciasController.class)
class InstanciasControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListarInstancias listarInstancias;

    @Test
    void get_instancias_devuelve_alias_y_salud_de_cada_una() throws Exception {
        Isbd isbd = new Isbd(Instant.now(), 90.0, Estado.OPTIMO,
            Optional.of(new Indicador(Componente.PROCESOS, 90.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 90.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 90.0, Map.of())),
            false, List.of(), false);
        InstanciaResumen resumen = new InstanciaResumen(new Instancia(new InstanciaId(1L), "principal"), Optional.of(isbd));
        when(listarInstancias.listar()).thenReturn(List.of(resumen));

        mvc.perform(get("/api/v1/instancias"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].alias").value("principal"))
            .andExpect(jsonPath("$[0].salud.puntuacion").value(90.0))
            .andExpect(jsonPath("$[0].salud.estado").value("OPTIMO"));
    }

    @Test
    void una_instancia_sin_ningun_muestreo_todavia_devuelve_salud_null_no_un_error() throws Exception {
        InstanciaResumen sinDatos =
            new InstanciaResumen(new Instancia(new InstanciaId(2L), "recien-agregada"), Optional.empty());
        when(listarInstancias.listar()).thenReturn(List.of(sinDatos));

        mvc.perform(get("/api/v1/instancias"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].alias").value("recien-agregada"))
            .andExpect(jsonPath("$[0].salud").doesNotExist());
    }

    @Test
    void get_instancias_marca_vetusto_igual_que_get_salud() throws Exception {
        // Mismo umbral que SaludControllerTest.get_salud_marca_vetusto_...: un
        // momento fijo y viejo debe marcar vetusto=true también en el tile de
        // la vista general, no solo en el dashboard de detalle.
        Isbd viejo = new Isbd(Instant.parse("2026-08-16T10:00:00Z"), 82.0, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 82.0, Map.of())),
            false, List.of(), false);
        InstanciaResumen resumen = new InstanciaResumen(new Instancia(new InstanciaId(1L), "principal"), Optional.of(viejo));
        when(listarInstancias.listar()).thenReturn(List.of(resumen));

        mvc.perform(get("/api/v1/instancias"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].salud.vetusto").value(true));
    }
}
