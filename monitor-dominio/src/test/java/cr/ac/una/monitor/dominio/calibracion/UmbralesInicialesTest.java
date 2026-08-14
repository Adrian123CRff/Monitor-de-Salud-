package cr.ac.una.monitor.dominio.calibracion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class UmbralesInicialesTest {

    static Stream<List<Umbral>> componentes() {
        return Stream.of(UmbralesIniciales.procesos(), UmbralesIniciales.memoria(), UmbralesIniciales.archivos());
    }

    @ParameterizedTest
    @MethodSource("componentes")
    void los_pesos_de_cada_componente_suman_uno(List<Umbral> umbrales) {
        double suma = umbrales.stream().mapToDouble(Umbral::pesoEnComponente).sum();
        assertThat(suma).isCloseTo(1.0, offset(0.001));
    }

    @Test
    void no_hay_variables_duplicadas_dentro_de_un_componente() {
        assertThat(UmbralesIniciales.procesos()).extracting(Umbral::variable).doesNotHaveDuplicates();
        assertThat(UmbralesIniciales.memoria()).extracting(Umbral::variable).doesNotHaveDuplicates();
        assertThat(UmbralesIniciales.archivos()).extracting(Umbral::variable).doesNotHaveDuplicates();
    }
}
