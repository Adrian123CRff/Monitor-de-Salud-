package cr.ac.una.monitor.dominio.calibracion;

import cr.ac.una.monitor.dominio.modelo.Componente;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CalibracionTest {

    @Test
    void inicial_usa_los_pesos_del_documento_de_diseno() {
        Calibracion cal = Calibracion.inicial();

        assertThat(cal.pesos())
            .containsEntry(Componente.PROCESOS, 0.30)
            .containsEntry(Componente.MEMORIA, 0.35)
            .containsEntry(Componente.ARCHIVOS, 0.35);
        assertThat(cal.vetoHabilitado()).isTrue();
        assertThat(cal.umbralVetoComponente()).isEqualTo(40.0);
    }

    @Test
    void rechaza_pesos_que_no_suman_uno() {
        Map<Componente, Double> pesosInvalidos = Map.of(
            Componente.PROCESOS, 0.30, Componente.MEMORIA, 0.30, Componente.ARCHIVOS, 0.30);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Calibracion(pesosInvalidos, true, 40.0))
            .withMessageContaining("deben sumar 1.0");
    }

    @Test
    void acepta_una_pequena_tolerancia_de_redondeo() {
        Map<Componente, Double> pesos = Map.of(
            Componente.PROCESOS, 0.30, Componente.MEMORIA, 0.35, Componente.ARCHIVOS, 0.3499);

        assertThat(new Calibracion(pesos, true, 40.0).pesos()).hasSize(3);
    }
}
