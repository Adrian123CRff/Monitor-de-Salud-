package cr.ac.una.monitor.dominio.alertas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EvaluadorNivelTest {

    // ADVERTENCIA 75/70, ALTO 90/85, CRITICO 98/95 -- igual que AlertasIniciales.peorTablespacePct().
    private final UmbralAlerta tablespace = UmbralAlerta.sinConfirmacion("peor_tablespace_pct",
        75, 70, 90, 85, 98, 95);

    @Test
    void por_debajo_de_entrada_advertencia_se_mantiene_normal() {
        assertThat(EvaluadorNivel.evaluar(50, Nivel.NORMAL, tablespace)).isEqualTo(Nivel.NORMAL);
    }

    @Test
    void cruzar_entrada_advertencia_sube_un_nivel() {
        assertThat(EvaluadorNivel.evaluar(76, Nivel.NORMAL, tablespace)).isEqualTo(Nivel.ADVERTENCIA);
    }

    @Test
    void un_salto_directo_a_critico_no_se_queda_atascado_en_advertencia_o_alto() {
        assertThat(EvaluadorNivel.evaluar(99, Nivel.NORMAL, tablespace)).isEqualTo(Nivel.CRITICO);
    }

    @Test
    void la_zona_muerta_entre_entrada_y_salida_mantiene_el_nivel_anterior() {
        // 72 está por debajo de la entrada (75) pero por encima de la salida (70):
        // con un umbral único ya habría bajado a NORMAL -- con histéresis, no.
        assertThat(EvaluadorNivel.evaluar(72, Nivel.ADVERTENCIA, tablespace)).isEqualTo(Nivel.ADVERTENCIA);
    }

    @Test
    void bajar_de_la_salida_de_advertencia_vuelve_a_normal() {
        assertThat(EvaluadorNivel.evaluar(69, Nivel.ADVERTENCIA, tablespace)).isEqualTo(Nivel.NORMAL);
    }

    @Test
    void una_caida_brusca_de_critico_a_normal_cascada_en_una_sola_llamada() {
        assertThat(EvaluadorNivel.evaluar(10, Nivel.CRITICO, tablespace)).isEqualTo(Nivel.NORMAL);
    }

    @Test
    void una_variable_binaria_salta_directo_a_critico_sin_pasar_por_advertencia_o_alto() {
        UmbralAlerta binaria = UmbralAlerta.binaria("a2_datafiles_offline", 1);

        assertThat(EvaluadorNivel.evaluar(1, Nivel.NORMAL, binaria)).isEqualTo(Nivel.CRITICO);
        assertThat(EvaluadorNivel.evaluar(0, Nivel.CRITICO, binaria)).isEqualTo(Nivel.NORMAL);
    }

    @Test
    void un_umbral_graduado_con_salida_en_un_valor_inalcanzable_nunca_cierra() {
        // Bug real encontrado preparando una prueba de estrés en vivo: para un conteo que
        // nunca es negativo, salidaAdvertencia=0 con < estricto (valor < 0) es inalcanzable
        // -- el episodio quedaría abierto para siempre. Documenta por qué
        // AlertasIniciales.sesionesBloqueadas()/presionPga() usan salidaAdvertencia=
        // entradaAdvertencia en vez de 0 (ver esas fábricas).
        UmbralAlerta conSalidaInalcanzable = UmbralAlerta.conConfirmacion("x", 1, 0, 3, 2, 5, 4, 2, 3);

        assertThat(EvaluadorNivel.evaluar(0, Nivel.ADVERTENCIA, conSalidaInalcanzable)).isEqualTo(Nivel.ADVERTENCIA);
    }

    @Test
    void umbrales_inconsistentes_fallan_en_la_construccion() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> UmbralAlerta.sinConfirmacion("x", 75, 80, 90, 85, 98, 95));
    }
}
