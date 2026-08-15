package cr.ac.una.monitor.dominio.alertas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmadorTemporalTest {

    @Test
    void tres_de_las_ultimas_cinco_confirma() {
        List<Boolean> ultimas = List.of(true, false, true, true, false);

        assertThat(ConfirmadorTemporal.confirmada(ultimas, 3, 5)).isTrue();
    }

    @Test
    void dos_de_las_ultimas_cinco_no_confirma_si_se_requieren_tres() {
        List<Boolean> ultimas = List.of(true, false, true, false, false);

        assertThat(ConfirmadorTemporal.confirmada(ultimas, 3, 5)).isFalse();
    }

    @Test
    void tolera_una_lectura_anomala_en_medio_de_una_condicion_real() {
        // 3 consecutivas fallaría con un solo falso positivo intercalado; "3 de 5" no.
        List<Boolean> ultimas = List.of(true, false, true, true, true);

        assertThat(ConfirmadorTemporal.confirmada(ultimas, 3, 5)).isTrue();
    }

    @Test
    void solo_mira_dentro_de_la_ventana_aunque_la_lista_sea_mas_larga() {
        List<Boolean> ultimas = List.of(false, false, true, true, true, true, true);

        assertThat(ConfirmadorTemporal.confirmada(ultimas, 3, 3)).isFalse();
    }
}
