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

    @Test
    void rechaza_un_peso_en_cero_aunque_la_suma_de_1() {
        // Encontrado por auditoría externa: {0.0, 0.0, 1.0} sumaba 1.0 y pasaba
        // -- si el componente con peso 1.0 fallara ese ciclo, MotorIndicadores
        // dividiría entre una suma de pesos presentes de 0 (NaN). Solo
        // PROCESOS va en cero aquí (los otros dos suman 1.0 entre ellos) para
        // que el mensaje sea determinista sin depender del orden de Map.forEach.
        Map<Componente, Double> pesos = Map.of(
            Componente.PROCESOS, 0.0, Componente.MEMORIA, 0.4, Componente.ARCHIVOS, 0.6);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Calibracion(pesos, true, 40.0))
            .withMessageContaining("PROCESOS")
            .withMessageContaining("mayor que 0");
    }

    @Test
    void rechaza_un_peso_negativo_aunque_la_suma_de_1() {
        Map<Componente, Double> pesos = Map.of(
            Componente.PROCESOS, -0.30, Componente.MEMORIA, 0.65, Componente.ARCHIVOS, 0.65);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Calibracion(pesos, true, 40.0))
            .withMessageContaining("mayor que 0");
    }

    @Test
    void rechaza_una_calibracion_que_no_trae_los_tres_componentes() {
        Map<Componente, Double> pesos = Map.of(Componente.PROCESOS, 0.5, Componente.MEMORIA, 0.5);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Calibracion(pesos, true, 40.0))
            .withMessageContaining("un peso para cada componente");
    }
}
