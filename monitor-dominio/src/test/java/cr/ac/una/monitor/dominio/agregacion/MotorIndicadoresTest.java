package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class MotorIndicadoresTest {

    private final MotorIndicadores motor = new MotorIndicadores();
    private final Instant ahora = Instant.parse("2026-08-13T22:00:00Z");

    private static Indicador indicador(Componente c, double puntuacion) {
        return new Indicador(c, puntuacion, Map.of());
    }

    @Test
    void todo_sano_da_el_mismo_promedio_que_cada_componente() {
        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 82),
            indicador(Componente.MEMORIA, 82),
            indicador(Componente.ARCHIVOS, 82),
            Calibracion.inicial());

        assertThat(isbd.puntuacion()).isCloseTo(82.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.SALUDABLE);
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.causas()).isEmpty();
    }

    @Test
    void ejemplo_de_la_seccion_19_del_documento_da_82_35_no_82_75() {
        // El .odt reporta 82.75; el cálculo correcto de 0.30(82)+0.35(74)+0.35(91) es 82.35.
        // Ver skill diseno-de-indicadores / references/agregacion.md.
        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 82),
            indicador(Componente.MEMORIA, 74),
            indicador(Componente.ARCHIVOS, 91),
            Calibracion.inicial());

        assertThat(isbd.puntuacion()).isCloseTo(82.35, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.SALUDABLE);
        assertThat(isbd.estadoPorVeto()).isFalse();
    }

    @Test
    void un_componente_critico_veta_el_estado_aunque_el_promedio_no_lo_refleje() {
        // Caso de la sección 20 del documento: procesos y memoria en rojo,
        // archivos perfecto. El promedio ponderado queda alto, pero el
        // estado real debe ser CRITICO.
        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 35),
            indicador(Componente.MEMORIA, 30),
            indicador(Componente.ARCHIVOS, 98),
            Calibracion.inicial());

        double promedioSinVeto = 0.30 * 35 + 0.35 * 30 + 0.35 * 98;
        assertThat(isbd.puntuacion()).isCloseTo(promedioSinVeto, offset(0.01)); // el número no miente
        assertThat(promedioSinVeto).isGreaterThan(40); // y aun así...
        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);                    // ...el estado sí
        assertThat(isbd.estadoPorVeto()).isTrue();
        assertThat(isbd.causas()).hasSize(2);
        assertThat(isbd.causas()).anyMatch(c -> c.contains("PROCESOS"));
        assertThat(isbd.causas()).anyMatch(c -> c.contains("MEMORIA"));
    }

    @Test
    void un_componente_justo_en_el_umbral_de_veto_no_veta() {
        // El veto es "< umbral", no "<= umbral" (Estado.DEGRADADO empieza en 40 inclusive).
        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 40),
            indicador(Componente.MEMORIA, 82),
            indicador(Componente.ARCHIVOS, 82),
            Calibracion.inicial());

        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.causas()).isEmpty();
    }

    @Test
    void con_el_veto_deshabilitado_el_estado_sale_solo_del_promedio() {
        Calibracion sinVeto = new Calibracion(Calibracion.inicial().pesos(), false, 40.0);

        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 10),
            indicador(Componente.MEMORIA, 100),
            indicador(Componente.ARCHIVOS, 100),
            sinVeto);

        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.causas()).isEmpty();
        assertThat(isbd.estado()).isEqualTo(Estado.desdePuntuacion(isbd.puntuacion()));
    }
}
