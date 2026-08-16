package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarHistorico;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarSalud;
import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.aplicacion.puerto.entrada.SaludNoDisponibleException;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice de MVC (sin Oracle/Postgres reales, ver JdbcRepositorioIndicesIT
 * para eso) -- valida el mapeo DTO/JSON y el manejo de errores de
 * ManejadorErrores, que hasta ahora solo se había probado a mano con curl.
 */
@WebMvcTest(SaludController.class)
class SaludControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarSalud consultarSalud;

    @MockitoBean
    private ConsultarHistorico consultarHistorico;

    @MockitoBean
    private MuestrearInstancia muestrearInstancia;

    private static Isbd isbdSano() {
        return new Isbd(Instant.parse("2026-08-16T10:00:00Z"), 82.35, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 74.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 91.0, Map.of())),
            false, List.of(), false);
    }

    @Test
    void get_salud_devuelve_el_isbd_actual() throws Exception {
        when(consultarSalud.actual(new InstanciaId(1L))).thenReturn(isbdSano());

        mvc.perform(get("/api/v1/instancias/1/salud"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.puntuacion").value(82.35))
            .andExpect(jsonPath("$.estado").value("SALUDABLE"))
            .andExpect(jsonPath("$.ip").value(82.0))
            .andExpect(jsonPath("$.parcial").value(false));
    }

    @Test
    void get_salud_sin_datos_todavia_devuelve_404_con_problem_detail() throws Exception {
        when(consultarSalud.actual(any())).thenThrow(new SaludNoDisponibleException(new InstanciaId(999L)));

        mvc.perform(get("/api/v1/instancias/999/salud"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void get_historico_delega_el_rango_pedido() throws Exception {
        when(consultarHistorico.enRango(eq(new InstanciaId(1L)), any(), any())).thenReturn(List.of(isbdSano()));

        mvc.perform(get("/api/v1/instancias/1/salud/historico")
                .param("desde", "2026-08-16T00:00:00Z")
                .param("hasta", "2026-08-17T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].estado").value("SALUDABLE"));
    }

    @Test
    void post_muestrear_dispara_el_caso_de_uso_y_devuelve_el_isbd_resultante() throws Exception {
        when(muestrearInstancia.ejecutar(new InstanciaId(1L))).thenReturn(isbdSano());

        mvc.perform(post("/api/v1/instancias/1/muestrear"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.puntuacion").value(82.35));
    }
}
