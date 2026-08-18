package cr.ac.una.monitor.dominio.agregacion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CalculadorDeltaTest {

    private static final Duration UN_CICLO = Duration.ofSeconds(60);

    @Test
    void sin_muestra_anterior_no_hay_delta_ni_reinicio() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.empty(), UN_CICLO);

        assertThat(r.delta()).isEmpty();
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_subiendo_da_una_delta_normal() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.of(120.0), UN_CICLO);

        assertThat(r.delta()).contains(30.0);
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_igual_da_delta_cero() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(120, Optional.of(120.0), UN_CICLO);

        assertThat(r.delta()).hasValueSatisfying(d -> assertThat(d).isCloseTo(0.0, offset(0.001)));
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void el_contador_bajando_se_interpreta_como_reinicio_sin_delta() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(5, Optional.of(9000.0), UN_CICLO);

        assertThat(r.delta()).isEmpty();
        assertThat(r.reinicioDetectado()).isTrue();
    }

    @Test
    void un_intervalo_demasiado_largo_descarta_la_delta_aunque_el_contador_suba() {
        // Encontrado por auditoría externa: un ciclo perdido (backend
        // reiniciado a mitad de intervalo, una consulta lenta) hacía que la
        // siguiente delta cubriera varios minutos en vez de uno, disparando
        // "presión de PGA" falsa. No es un reinicio de instancia (el contador
        // sí subió) -- es "no comparable", se trata igual que sin historial.
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.of(120.0), Duration.ofMinutes(10));

        assertThat(r.delta()).isEmpty();
        assertThat(r.reinicioDetectado()).isFalse();
    }

    @Test
    void un_intervalo_justo_por_debajo_del_limite_si_se_compara() {
        CalculadorDelta.Resultado r = CalculadorDelta.calcular(150, Optional.of(120.0), Duration.ofSeconds(179));

        assertThat(r.delta()).contains(30.0);
    }
}
