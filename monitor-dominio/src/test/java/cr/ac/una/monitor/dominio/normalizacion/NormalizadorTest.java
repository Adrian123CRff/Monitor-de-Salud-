package cr.ac.una.monitor.dominio.normalizacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.offset;

class NormalizadorTest {

    // Tabla de la skill diseno-de-indicadores: util_procesos_pct, ok=70, critico=95.
    @ParameterizedTest
    @CsvSource({
        "30, 100.0",
        "70, 100.0",
        "80, 60.0",
        "90, 20.0",
        "95, 0.0",
    })
    void linealInvertida_reproduce_la_tabla_de_utilizacion_de_procesos(double utilizacion, double esperado) {
        assertThat(Normalizador.linealInvertida(utilizacion, 70, 95))
            .isCloseTo(esperado, offset(0.01));
    }

    @Test
    void linealInvertida_exige_critico_mayor_que_ok() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Normalizador.linealInvertida(50, 95, 70));
    }

    @Test
    void linealDirecta_puntua_100_cuando_el_valor_alcanza_el_ok() {
        // cache_hit_pct: critico=50, ok=90
        assertThat(Normalizador.linealDirecta(95, 50, 90)).isEqualTo(100.0);
        assertThat(Normalizador.linealDirecta(50, 50, 90)).isEqualTo(0.0);
        assertThat(Normalizador.linealDirecta(70, 50, 90)).isCloseTo(50.0, offset(0.01));
    }

    @Test
    void linealDirecta_exige_ok_mayor_que_critico() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Normalizador.linealDirecta(50, 90, 70));
    }

    @Test
    void porTramos_respeta_la_clasificacion_normal_advertencia_alto_critico() {
        List<Tramo> tramos = List.of(
            new Tramo(0, 70, 100, 85),
            new Tramo(70, 85, 85, 60),
            new Tramo(85, 95, 60, 30),
            new Tramo(95, 101, 30, 0));

        // Interpolación lineal dentro del tramo, no una meseta: a valor=30 (dentro
        // de [0,70) -> [100,85]) el puntaje ya bajó proporcionalmente, no sigue en 100.
        assertThat(Normalizador.porTramos(30, tramos)).isCloseTo(93.57, offset(0.01));
        assertThat(Normalizador.porTramos(77.5, tramos)).isCloseTo(72.5, offset(0.01));
        assertThat(Normalizador.porTramos(98, tramos)).isCloseTo(15.0, offset(0.01));
        assertThat(Normalizador.porTramos(0, tramos)).isEqualTo(100.0);
        assertThat(Normalizador.porTramos(-5, tramos)).isEqualTo(100.0); // por debajo del primer tramo
    }

    @Test
    void penalizacionDiscreta_resta_puntos_por_evento_sin_bajar_del_piso() {
        assertThat(Normalizador.penalizacionDiscreta(0, 25, 0)).isEqualTo(100.0);
        assertThat(Normalizador.penalizacionDiscreta(2, 25, 0)).isEqualTo(50.0);
        assertThat(Normalizador.penalizacionDiscreta(10, 25, 0)).isEqualTo(0.0); // no negativo
    }

    @Test
    void criticoSiHayAlguno_no_admite_grados() {
        assertThat(Normalizador.criticoSiHayAlguno(0)).isEqualTo(100.0);
        assertThat(Normalizador.criticoSiHayAlguno(1)).isEqualTo(0.0);
        assertThat(Normalizador.criticoSiHayAlguno(5)).isEqualTo(0.0);
    }
}
