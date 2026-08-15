package cr.ac.una.monitor.dominio.agregacion;

import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static cr.ac.una.monitor.dominio.calibracion.TipoUmbral.LINEAL_INVERTIDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.offset;

class CalculadorComponenteTest {

    private final CalculadorComponente calculador = new CalculadorComponente();
    private final Instant ahora = Instant.parse("2026-08-14T10:00:00Z");

    private Muestra muestraProcesos(double utilProcesos, double utilSesiones, double bloqueados, double bloqueoMaxSeg) {
        return new Muestra(Componente.PROCESOS, ahora, Map.of(
            "util_procesos_pct", utilProcesos,
            "util_sesiones_pct", utilSesiones,
            "p6_sesiones_bloqueadas", bloqueados,
            "bloqueo_max_seg", bloqueoMaxSeg
        ), false);
    }

    @Test
    void una_instancia_sana_da_una_puntuacion_alta() {
        Indicador ip = calculador.calcular(
            muestraProcesos(30, 25, 0, 0), Componente.PROCESOS, UmbralesIniciales.procesosUsuarios());

        assertThat(ip.puntuacion()).isCloseTo(100.0, offset(0.01));
        assertThat(ip.componente()).isEqualTo(Componente.PROCESOS);
    }

    @Test
    void los_bloqueos_penalizan_aunque_la_utilizacion_este_bien() {
        Indicador sano = calculador.calcular(
            muestraProcesos(30, 25, 0, 0), Componente.PROCESOS, UmbralesIniciales.procesosUsuarios());
        Indicador conBloqueos = calculador.calcular(
            muestraProcesos(30, 25, 2, 10), Componente.PROCESOS, UmbralesIniciales.procesosUsuarios());

        assertThat(conBloqueos.puntuacion()).isLessThan(sano.puntuacion());
        assertThat(conBloqueos.puntuacionesPorVariable().get("p6_sesiones_bloqueadas")).isCloseTo(50.0, offset(0.01));
    }

    @Test
    void un_datafile_offline_hunde_archivos_por_criticoSiHayAlguno() {
        Muestra muestra = new Muestra(Componente.ARCHIVOS, ahora, Map.of(
            "peor_tablespace_pct", 30.0,
            "a2_datafiles_offline", 1.0,
            "a7_archivos_invalidos", 0.0,
            "a8_archivos_recover", 0.0,
            "redundancia_redo", 2.0
        ), false);

        Indicador ia = calculador.calcular(muestra, Componente.ARCHIVOS, UmbralesIniciales.archivos());

        assertThat(ia.puntuacionesPorVariable().get("a2_datafiles_offline")).isEqualTo(0.0);
        // 40% peor_tablespace(100) + 20% a2(0) + 20% a7(100) + 10% a8(100) + 10% redundancia(100) = 80
        assertThat(ia.puntuacion()).isCloseTo(80.0, offset(0.01));
    }

    @Test
    void variables_ausentes_en_la_muestra_se_ignoran_sin_romper() {
        // Simula un adaptador que todavía no recolecta bloqueo_max_seg.
        Muestra muestraParcial = new Muestra(Componente.PROCESOS, ahora, Map.of(
            "util_procesos_pct", 30.0,
            "util_sesiones_pct", 25.0,
            "p6_sesiones_bloqueadas", 0.0
        ), false);

        Indicador ip = calculador.calcular(muestraParcial, Componente.PROCESOS, UmbralesIniciales.procesosUsuarios());

        assertThat(ip.puntuacionesPorVariable()).doesNotContainKey("bloqueo_max_seg");
        assertThat(ip.puntuacion()).isCloseTo(100.0, offset(0.01));
    }

    @Test
    void si_ninguna_variable_coincide_falla_de_forma_ruidosa_en_vez_de_inventar_un_100() {
        Muestra muestraVacia = new Muestra(Componente.PROCESOS, ahora, Map.of("variable_que_no_existe", 1.0), false);

        assertThatIllegalStateException()
            .isThrownBy(() -> calculador.calcular(muestraVacia, Componente.PROCESOS, UmbralesIniciales.procesosUsuarios()));
    }

    @Test
    void ignora_umbrales_de_tipo_contexto() {
        List<Umbral> conContexto = List.of(
            Umbral.lineal("variable_puntua", LINEAL_INVERTIDA, 70, 95, 1.0),
            new Umbral("variable_contexto", cr.ac.una.monitor.dominio.calibracion.TipoUmbral.CONTEXTO, 0, 0, 0, 0));
        Muestra muestra = new Muestra(Componente.MEMORIA, ahora,
            Map.of("variable_puntua", 30.0, "variable_contexto", 999.0), false);

        Indicador im = calculador.calcular(muestra, Componente.MEMORIA, conContexto);

        assertThat(im.puntuacionesPorVariable()).doesNotContainKey("variable_contexto");
        assertThat(im.puntuacion()).isCloseTo(100.0, offset(0.01));
    }
}
