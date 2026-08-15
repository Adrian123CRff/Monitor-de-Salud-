package cr.ac.una.monitor.dominio.agregacion;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CalculadorDeltaTest {

    @Test
    void sin_muestra_anterior_no_hay_delta_ni_reinicio() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.empty());

        assertThat(r.delta()).isEmpty();
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_subiendo_da_una_delta_normal() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.of(120.0));

        assertThat(r.delta()).contains(30.0);
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_igual_da_delta_cero() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(120, Optional.of(120.0));

        assertThat(r.delta()).hasValueSatisfying(d -> assertThat(d).isCloseTo(0.0, offset(0.001)));
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_bajando_se_interpreta_como_reinicio_sin_delta() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(5, Optional.of(9000.0));

        assertThat(r.delta()).isEmpty();
        assertThat(r.reinicioDetectado()).isTrue();
    }
}
