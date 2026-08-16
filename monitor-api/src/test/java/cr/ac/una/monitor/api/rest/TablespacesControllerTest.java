package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioTablespaces;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TablespacesController.class)
class TablespacesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioTablespaces tablespaces;

    @Test
    void get_tablespaces_devuelve_el_detalle_del_ultimo_ciclo() throws Exception {
        when(tablespaces.ultimo(new InstanciaId(1L))).thenReturn(List.of(
            new DetalleTablespace("USERS", 62.0, 620_000_000.0, 1_000_000_000.0),
            new DetalleTablespace("SYSTEM", 91.5, 915_000_000.0, 1_000_000_000.0)));

        mvc.perform(get("/api/v1/instancias/1/tablespaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].nombre").value("SYSTEM"))
            .andExpect(jsonPath("$[1].usedPercent").value(91.5));
    }

    @Test
    void get_tablespaces_sin_datos_todavia_devuelve_lista_vacia_no_error() throws Exception {
        when(tablespaces.ultimo(new InstanciaId(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/instancias/1/tablespaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
