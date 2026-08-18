package cr.ac.una.monitor.dominio.modelo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EstadoTest {

    @Test
    void limites_de_cada_franja() {
        assertThat(Estado.desdePuntuacion(0)).isEqualTo(Estado.CRITICO);
        assertThat(Estado.desdePuntuacion(39.99)).isEqualTo(Estado.CRITICO);
        assertThat(Estado.desdePuntuacion(40)).isEqualTo(Estado.DEGRADADO);
        assertThat(Estado.desdePuntuacion(59.99)).isEqualTo(Estado.DEGRADADO);
        assertThat(Estado.desdePuntuacion(60)).isEqualTo(Estado.ADVERTENCIA);
        assertThat(Estado.desdePuntuacion(74.99)).isEqualTo(Estado.ADVERTENCIA);
        assertThat(Estado.desdePuntuacion(75)).isEqualTo(Estado.SALUDABLE);
        assertThat(Estado.desdePuntuacion(89.99)).isEqualTo(Estado.SALUDABLE);
        assertThat(Estado.desdePuntuacion(90)).isEqualTo(Estado.OPTIMO);
        assertThat(Estado.desdePuntuacion(100)).isEqualTo(Estado.OPTIMO);
    }

    @Test
    void rechaza_una_puntuacion_por_encima_de_cien() {
        // Encontrado por auditoría externa: antes de la guarda explícita, 150
        // caía en la rama "e == OPTIMO" (que nunca revisa el límite superior,
        // a propósito, para que 100.0 exacto cuente como ÓPTIMO) y devolvía
        // OPTIMO en silencio en vez de lanzar.
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Estado.desdePuntuacion(150))
            .withMessageContaining("fuera de [0,100]");
    }

    @Test
    void rechaza_una_puntuacion_negativa() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Estado.desdePuntuacion(-1));
    }

    @Test
    void rechaza_nan() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Estado.desdePuntuacion(Double.NaN));
    }

    @Test
    void tolera_el_ruido_de_punto_flotante_justo_encima_de_cien() {
        // Regresión: la primera versión de la guarda contra p=150 (arriba)
        // era demasiado estricta y rechazaba 100.00000000000001 -- el
        // resultado real de sumar 0.30*100 + 0.35*100 + 0.35*100 en IEEE 754
        // (MotorIndicadoresTest/MuestrearInstanciaServicioTest lo produjeron
        // en varios escenarios reales de "todo sano").
        assertThat(Estado.desdePuntuacion(100.00000000000001)).isEqualTo(Estado.OPTIMO);
    }
}
