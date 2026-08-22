package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VariableEvaluada;
import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente.VistaComponente;
import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComponentesController.class)
class ComponentesControllerTest {

    private static final Instant MOMENTO = Instant.parse("2026-08-16T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarComponente consultarComponente;

    @Test
    void get_procesos_combina_usuarios_y_fondo_bajo_claves_distintas() throws Exception {
        VistaComponente usuarios = new VistaComponente(Componente.PROCESOS, MOMENTO,
            Map.of("p1_sesiones_activas", 12.0), 100.0, false, List.of());
        VistaComponente fondo = new VistaComponente(Componente.PROCESOS, MOMENTO,
            Map.of("b2_jobs_fallidos", 0.0), 100.0, false, List.of());
        when(consultarComponente.detalle(new InstanciaId(1L), Componente.PROCESOS))
            .thenReturn(Map.of("usuarios", usuarios, "fondo", fondo));

        mvc.perform(get("/api/v1/instancias/1/componentes/PROCESOS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarios.valores.p1_sesiones_activas").value(12.0))
            .andExpect(jsonPath("$.fondo.valores.b2_jobs_fallidos").value(0.0));
    }

    @Test
    void get_memoria_no_incluye_fondo() throws Exception {
        VistaComponente actual = new VistaComponente(Componente.MEMORIA, MOMENTO,
            Map.of("m1_sga_used_pct", 55.0), 90.0, false, List.of());
        when(consultarComponente.detalle(new InstanciaId(1L), Componente.MEMORIA))
            .thenReturn(Map.of("actual", actual));

        mvc.perform(get("/api/v1/instancias/1/componentes/MEMORIA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actual.valores.m1_sga_used_pct").value(55.0))
            .andExpect(jsonPath("$.fondo").doesNotExist());
    }

    /**
     * Lo que pidió el profesor: entrar a un componente en rojo y ver CUÁL
     * variable está fuera de límites, no solo el número del componente.
     */
    @Test
    void get_devuelve_el_desglose_por_variable_con_su_aporte_perdido() throws Exception {
        VistaComponente archivos = new VistaComponente(Componente.ARCHIVOS, MOMENTO,
            Map.of("a6_min_miembros_grupo", 1.0, "peor_tablespace_pct", 40.0),
            90.0, false,
            List.of(
                // redundancia_redo en 0 con peso 0.10 -> se lleva 10 puntos del componente
                new VariableEvaluada("redundancia_redo", 1.0, 0.0, 0.10, false,
                    TipoUmbral.LINEAL_DIRECTA, 2, 1),
                new VariableEvaluada("peor_tablespace_pct", 40.0, 100.0, 0.40, false,
                    TipoUmbral.LINEAL_INVERTIDA, 75, 95)));
        when(consultarComponente.detalle(new InstanciaId(1L), Componente.ARCHIVOS))
            .thenReturn(Map.of("actual", archivos));

        mvc.perform(get("/api/v1/instancias/1/componentes/ARCHIVOS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actual.puntuacion").value(90.0))
            .andExpect(jsonPath("$.actual.variables[0].variable").value("redundancia_redo"))
            .andExpect(jsonPath("$.actual.variables[0].puntuacion").value(0.0))
            .andExpect(jsonPath("$.actual.variables[0].valor").value(1.0))
            .andExpect(jsonPath("$.actual.variables[0].aportePerdido").value(10.0))
            .andExpect(jsonPath("$.actual.variables[1].aportePerdido").value(0.0))
            // Los limites viajan para que la ayuda contextual diga donde esta el
            // corte sin escribirlo a mano en el frontend.
            .andExpect(jsonPath("$.actual.variables[1].valorOk").value(75.0))
            .andExpect(jsonPath("$.actual.variables[1].valorCritico").value(95.0))
            .andExpect(jsonPath("$.actual.variables[1].tipoUmbral").value("LINEAL_INVERTIDA"));
    }

    /** Una muestra que no se pudo puntuar sigue mostrando el crudo, con puntuacion null. */
    @Test
    void get_sin_puntuacion_devuelve_el_crudo_igual() throws Exception {
        VistaComponente sinPuntuar = new VistaComponente(Componente.MEMORIA, MOMENTO,
            Map.of("m5_pga_asignada_bytes", 1024.0), null, null, List.of());
        when(consultarComponente.detalle(new InstanciaId(1L), Componente.MEMORIA))
            .thenReturn(Map.of("actual", sinPuntuar));

        mvc.perform(get("/api/v1/instancias/1/componentes/MEMORIA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actual.valores.m5_pga_asignada_bytes").value(1024.0))
            .andExpect(jsonPath("$.actual.puntuacion").doesNotExist())
            .andExpect(jsonPath("$.actual.variables").isEmpty());
    }

    @Test
    void get_sin_muestras_todavia_devuelve_404() throws Exception {
        when(consultarComponente.detalle(new InstanciaId(1L), Componente.ARCHIVOS)).thenReturn(Map.of());

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
