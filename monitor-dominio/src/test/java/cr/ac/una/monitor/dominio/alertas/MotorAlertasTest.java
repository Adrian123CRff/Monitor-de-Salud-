package cr.ac.una.monitor.dominio.alertas;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MotorAlertasTest {

    private final MotorAlertas motor = new MotorAlertas();
    private final InstanciaId instancia = new InstanciaId(1L);

    private Alerta alerta(Nivel nivel) {
        return new Alerta(1L, instancia, Componente.ARCHIVOS, "peor_tablespace_pct", Optional.of("USERS"),
            nivel, 93.0, 90.0, "USERS al 93%", Instant.parse("2026-08-15T10:00:00Z"), Optional.empty());
    }

    @Test
    void normal_sin_alerta_abierta_no_hace_nada() {
        ResultadoEvaluacion r = motor.evaluar(Nivel.NORMAL, Optional.empty(), () -> {
            throw new AssertionError("no debería construir una alerta nueva");
        });

        assertThat(r).isInstanceOf(ResultadoEvaluacion.SinCambios.class);
    }

    @Test
    void nivel_no_normal_sin_alerta_previa_abre_una_nueva() {
        Alerta nueva = alerta(Nivel.ADVERTENCIA);

        ResultadoEvaluacion r = motor.evaluar(Nivel.ADVERTENCIA, Optional.empty(), () -> nueva);

        assertThat(r).isInstanceOf(ResultadoEvaluacion.Abrir.class);
        assertThat(((ResultadoEvaluacion.Abrir) r).nueva()).isSameAs(nueva);
    }

    @Test
    void normal_con_alerta_abierta_la_cierra() {
        Alerta abierta = alerta(Nivel.ADVERTENCIA);

        ResultadoEvaluacion r = motor.evaluar(Nivel.NORMAL, Optional.of(abierta), () -> {
            throw new AssertionError("no debería construir una alerta nueva");
        });

        assertThat(r).isInstanceOf(ResultadoEvaluacion.Cerrar.class);
        assertThat(((ResultadoEvaluacion.Cerrar) r).existente()).isSameAs(abierta);
    }

    @Test
    void mismo_nivel_que_la_alerta_abierta_no_hace_nada_deduplicacion() {
        Alerta abierta = alerta(Nivel.ALTO);

        ResultadoEvaluacion r = motor.evaluar(Nivel.ALTO, Optional.of(abierta), () -> {
            throw new AssertionError("no debería construir una alerta nueva");
        });

        assertThat(r).isInstanceOf(ResultadoEvaluacion.SinCambios.class);
    }

    @Test
    void escalar_de_nivel_cierra_la_anterior_y_abre_una_nueva() {
        Alerta abierta = alerta(Nivel.ADVERTENCIA);
        Alerta nueva = alerta(Nivel.CRITICO);

        ResultadoEvaluacion r = motor.evaluar(Nivel.CRITICO, Optional.of(abierta), () -> nueva);

        assertThat(r).isInstanceOf(ResultadoEvaluacion.CerrarYAbrir.class);
        var cerrarYAbrir = (ResultadoEvaluacion.CerrarYAbrir) r;
        assertThat(cerrarYAbrir.aCerrar()).isSameAs(abierta);
        assertThat(cerrarYAbrir.aAbrir()).isSameAs(nueva);
    }
}
