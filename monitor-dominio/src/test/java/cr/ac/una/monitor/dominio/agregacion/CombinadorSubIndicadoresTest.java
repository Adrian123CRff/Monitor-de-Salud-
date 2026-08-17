package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.offset;

class CombinadorSubIndicadoresTest {

    private final CombinadorSubIndicadores combinador = new CombinadorSubIndicadores();

    @Test
    void combina_usuarios_y_fondo_con_los_pesos_dados() {
        Indicador usuarios = new Indicador(Componente.PROCESOS, 90, Map.of("util_procesos_pct", 90.0));
        Indicador fondo = new Indicador(Componente.PROCESOS, 50, Map.of("b1_procesos_caidos", 50.0));

        Indicador ip = combinador.combinar(Componente.PROCESOS,
            Map.of("usuarios", usuarios, "fondo", fondo),
            Map.of("usuarios", 0.4, "fondo", 0.6));

        // 0.4*90 + 0.6*50 = 66
        assertThat(ip.puntuacion()).isCloseTo(66.0, offset(0.01));
        assertThat(ip.componente()).isEqualTo(Componente.PROCESOS);
    }

    @Test
    void el_desglose_conserva_las_variables_de_ambos_sub_indicadores_con_prefijo() {
        Indicador usuarios = new Indicador(Componente.PROCESOS, 90, Map.of("util_procesos_pct", 90.0));
        Indicador fondo = new Indicador(Componente.PROCESOS, 50, Map.of("b1_procesos_caidos", 50.0));

        Indicador ip = combinador.combinar(Componente.PROCESOS,
            Map.of("usuarios", usuarios, "fondo", fondo),
            Map.of("usuarios", 0.4, "fondo", 0.6));

        assertThat(ip.puntuacionesPorVariable())
            .containsEntry("usuarios.util_procesos_pct", 90.0)
            .containsEntry("fondo.b1_procesos_caidos", 50.0);
    }

    @Test
    void falla_si_falta_el_peso_de_un_sub_indicador() {
        Indicador usuarios = new Indicador(Componente.PROCESOS, 90, Map.of());

        assertThatIllegalArgumentException().isThrownBy(() ->
            combinador.combinar(Componente.PROCESOS, Map.of("usuarios", usuarios), Map.of()));
    }

    @Test
    void falla_si_no_hay_ningun_sub_indicador() {
        assertThatIllegalStateException().isThrownBy(() ->
            combinador.combinar(Componente.PROCESOS, Map.of(), Map.of()));
    }

    @Test
    void un_solo_sub_indicador_vetado_veta_el_combinado_aunque_la_puntuacion_no_lo_sugiera() {
        // Caso real (ver MuestrearInstanciaServicioTest): IP_usuarios=100 sano,
        // IP_fondo=0 vetado por un proceso mandatorio caído. Con pesos 0.40/0.60
        // el combinado da exactamente 40.0 -- un valor que, mirado solo como
        // número, no dice "vetado" por sí mismo. vetado=true debe propagarse
        // igual, sin depender de dónde caiga la puntuación combinada.
        Indicador usuarios = new Indicador(Componente.PROCESOS, 100, false, Map.of());
        Indicador fondo = new Indicador(Componente.PROCESOS, 0, true, Map.of("b1_procesos_caidos", 0.0));

        Indicador ip = combinador.combinar(Componente.PROCESOS,
            Map.of("usuarios", usuarios, "fondo", fondo),
            Map.of("usuarios", 0.4, "fondo", 0.6));

        assertThat(ip.puntuacion()).isCloseTo(40.0, offset(0.01));
        assertThat(ip.vetado()).isTrue();
    }

    @Test
    void ningun_sub_indicador_vetado_no_veta_el_combinado() {
        Indicador usuarios = new Indicador(Componente.PROCESOS, 90, Map.of());
        Indicador fondo = new Indicador(Componente.PROCESOS, 50, Map.of());

        Indicador ip = combinador.combinar(Componente.PROCESOS,
            Map.of("usuarios", usuarios, "fondo", fondo),
            Map.of("usuarios", 0.4, "fondo", 0.6));

        assertThat(ip.vetado()).isFalse();
    }
}
