package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.offset;

class MotorIndicadoresTest {

    private final MotorIndicadores motor = new MotorIndicadores();
    private final Instant ahora = Instant.parse("2026-08-13T22:00:00Z");

    private static Optional<Indicador> indicador(Componente c, double puntuacion) {
        return Optional.of(new Indicador(c, puntuacion, Map.of()));
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
        assertThat(isbd.parcial()).isFalse();
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

    @Test
    void un_componente_ausente_se_excluye_y_redistribuye_el_peso_sin_vetar() {
        // MEMORIA no se pudo recolectar este ciclo (RecoleccionFallidaException,
        // ver MuestrearInstanciaServicio.recolectarSeguro) -- no es lo mismo que
        // "está mal": no debe vetar ni entrar en el promedio como si fuera 0.
        Isbd isbd = motor.calcular(ahora,
            indicador(Componente.PROCESOS, 85),
            Optional.empty(),
            indicador(Componente.ARCHIVOS, 90),
            Calibracion.inicial());

        double esperado = (0.30 * 85 + 0.35 * 90) / (0.30 + 0.35);
        assertThat(isbd.puntuacion()).isCloseTo(esperado, offset(0.01));
        assertThat(isbd.parcial()).isTrue();
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.im()).isEmpty();
        assertThat(isbd.causas()).containsExactly("MEMORIA: fallo de recolección, excluido del cálculo");
    }

    @Test
    void un_indicador_vetado_veta_el_estado_aunque_su_puntuacion_este_alta() {
        // Distinto del test de "un_componente_critico_veta..." de arriba: ahí el
        // veto sale de la puntuación numérica (< umbral). Este caso prueba la
        // marca vetado=true directamente (ver CalculadorComponente/
        // CombinadorSubIndicadores) -- un Indicador puede llegar aquí con
        // puntuación ALTA y aun así estar vetado, si viene de combinar un
        // sub-indicador sano con uno vetado (ver CombinadorSubIndicadoresTest).
        // 90 está muy por encima del umbral de veto (40): sin la marca, esto
        // pasaría en verde.
        Isbd isbd = motor.calcular(ahora,
            Optional.of(new Indicador(Componente.PROCESOS, 90, true, Map.of())),
            indicador(Componente.MEMORIA, 90),
            indicador(Componente.ARCHIVOS, 90),
            Calibracion.inicial());

        assertThat(isbd.puntuacion()).isCloseTo(90.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.estadoPorVeto()).isTrue();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("PROCESOS") && c.contains("vetado"));
    }

    @Test
    void con_solo_un_componente_presente_el_estado_se_topa_en_advertencia() {
        // Encontrado por auditoría externa: PROCESOS y ARCHIVOS fallaron el
        // mismo ciclo, solo MEMORIA respondió y está sano (95). Sin el tope,
        // el ISBD sería literalmente 95 -> OPTIMO, con dos tercios del
        // sistema ilegible -- "parcial=true" es fácil de pasar por alto si
        // solo se mira el semáforo.
        Isbd isbd = motor.calcular(ahora,
            Optional.empty(),
            indicador(Componente.MEMORIA, 95),
            Optional.empty(),
            Calibracion.inicial());

        assertThat(isbd.puntuacion()).isCloseTo(95.0, offset(0.01));
        assertThat(isbd.estado()).isEqualTo(Estado.ADVERTENCIA);
        assertThat(isbd.estadoPorVeto()).isFalse();
        assertThat(isbd.parcial()).isTrue();
        assertThat(isbd.causas()).anyMatch(c -> c.contains("Cobertura insuficiente"));
    }

    @Test
    void con_un_solo_componente_presente_que_ya_es_critico_no_hace_falta_topar() {
        Isbd isbd = motor.calcular(ahora,
            Optional.empty(),
            indicador(Componente.MEMORIA, 10),
            Optional.empty(),
            Calibracion.inicial());

        assertThat(isbd.estado()).isEqualTo(Estado.CRITICO);
        assertThat(isbd.causas()).noneMatch(c -> c.contains("Cobertura insuficiente"));
    }

    @Test
    void si_no_se_pudo_recolectar_ningun_componente_no_hay_nada_que_calcular() {
        assertThatIllegalStateException()
            .isThrownBy(() -> motor.calcular(ahora, Optional.empty(), Optional.empty(), Optional.empty(),
                Calibracion.inicial()))
            .withMessageContaining("nada que calcular");
    }
}
