package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioAlertas;
import cr.ac.una.monitor.dominio.alertas.Alerta;
import cr.ac.una.monitor.dominio.alertas.Nivel;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertasController.class)
class AlertasControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioAlertas alertas;

    @Test
    void get_alertas_devuelve_las_abiertas_en_el_orden_del_repositorio() throws Exception {
        InstanciaId instancia = new InstanciaId(1L);
        Alerta critica = new Alerta(1L, instancia, Componente.ARCHIVOS, "peor_tablespace_pct",
            Optional.of("SYSTEM"), Nivel.CRITICO, 98.2, 98.0, "Tablespace SYSTEM al 98.2%",
            Instant.parse("2026-08-16T09:00:00Z"), Optional.empty());
        Alerta advertencia = new Alerta(2L, instancia, Componente.PROCESOS, "a2_datafiles_offline",
            Optional.empty(), Nivel.ADVERTENCIA, 1.0, 1.0, "Datafile offline detectado",
            Instant.parse("2026-08-16T09:30:00Z"), Optional.empty());
        when(alertas.abiertas(instancia)).thenReturn(List.of(critica, advertencia));

        mvc.perform(get("/api/v1/instancias/1/alertas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nivel").value("CRITICO"))
            .andExpect(jsonPath("$[0].entidad").value("SYSTEM"))
            .andExpect(jsonPath("$[1].nivel").value("ADVERTENCIA"))
            .andExpect(jsonPath("$[1].entidad").doesNotExist());
    }

    @Test
    void get_alertas_sin_ninguna_abierta_devuelve_lista_vacia() throws Exception {
        when(alertas.abiertas(new InstanciaId(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/instancias/1/alertas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
