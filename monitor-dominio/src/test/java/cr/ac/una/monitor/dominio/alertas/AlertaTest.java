package cr.ac.una.monitor.dominio.alertas;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AlertaTest {

    private final InstanciaId instancia = new InstanciaId(1L);

    @Test
    void una_alerta_con_nivel_normal_no_tiene_sentido_y_falla_en_la_construccion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Alerta(null, instancia, Componente.ARCHIVOS,
            "peor_tablespace_pct", Optional.empty(), Nivel.NORMAL, 50.0, 75.0, "x",
            Instant.now(), Optional.empty()));
    }

    @Test
    void cerrar_conserva_todo_lo_demas_y_solo_pone_cerrada_en() {
        Alerta abierta = new Alerta(1L, instancia, Componente.ARCHIVOS, "peor_tablespace_pct",
            Optional.of("USERS"), Nivel.ADVERTENCIA, 76.0, 75.0, "USERS al 76%",
            Instant.parse("2026-08-15T10:00:00Z"), Optional.empty());

        Alerta cerrada = abierta.cerrar(Instant.parse("2026-08-15T10:30:00Z"));

        assertThat(abierta.abierta()).isTrue();
        assertThat(cerrada.abierta()).isFalse();
        assertThat(cerrada.cerradaEn()).contains(Instant.parse("2026-08-15T10:30:00Z"));
        assertThat(cerrada.id()).isEqualTo(abierta.id());
        assertThat(cerrada.nivel()).isEqualTo(abierta.nivel());
        assertThat(cerrada.abiertaEn()).isEqualTo(abierta.abiertaEn());
    }
}
