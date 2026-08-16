package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioCalibracion;
import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalibracionController.class)
class CalibracionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioCalibracion calibraciones;

    @Test
    void get_calibracion_sin_ninguna_registrada_devuelve_la_inicial() throws Exception {
        when(calibraciones.vigente()).thenReturn(null);

        mvc.perform(get("/api/v1/calibracion"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pesos.PROCESOS").value(0.30))
            .andExpect(jsonPath("$.umbralVetoComponente").value(40.0));
    }

    @Test
    void get_calibracion_devuelve_la_vigente_cuando_hay_una_registrada() throws Exception {
        when(calibraciones.vigente()).thenReturn(new Calibracion(
            Map.of(Componente.PROCESOS, 0.25, Componente.MEMORIA, 0.40, Componente.ARCHIVOS, 0.35),
            false, 35.0));

        mvc.perform(get("/api/v1/calibracion"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pesos.MEMORIA").value(0.40))
            .andExpect(jsonPath("$.vetoHabilitado").value(false));
    }

    @Test
    void put_calibracion_registra_y_devuelve_lo_registrado() throws Exception {
        String cuerpo = """
            {"pesos":{"PROCESOS":0.20,"MEMORIA":0.40,"ARCHIVOS":0.40},"vetoHabilitado":true,"umbralVetoComponente":45.0}
            """;

        mvc.perform(put("/api/v1/calibracion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pesos.PROCESOS").value(0.20))
            .andExpect(jsonPath("$.umbralVetoComponente").value(45.0));

        ArgumentCaptor<Calibracion> capturada = ArgumentCaptor.forClass(Calibracion.class);
        verify(calibraciones).registrar(capturada.capture());
        assertThat(capturada.getValue().pesos().get(Componente.MEMORIA)).isEqualTo(0.40);
    }

    @Test
    void put_calibracion_con_pesos_que_no_suman_uno_devuelve_400() throws Exception {
        String cuerpo = """
            {"pesos":{"PROCESOS":0.20,"MEMORIA":0.40,"ARCHIVOS":0.10},"vetoHabilitado":true,"umbralVetoComponente":45.0}
            """;

        mvc.perform(put("/api/v1/calibracion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isBadRequest());
    }
}
