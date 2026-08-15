package cr.ac.una.monitor.infraestructura.planificador;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PlanificadorMuestreoTest {

    @Test
    void muestrea_la_instancia_configurada() {
        List<InstanciaId> llamadas = new ArrayList<>();
        MuestrearInstancia casoFalso = instancia -> {
            llamadas.add(instancia);
            return null;
        };

        new PlanificadorMuestreo(casoFalso, 7L).muestrear();

        assertThat(llamadas).containsExactly(new InstanciaId(7L));
    }

    @Test
    void una_excepcion_del_caso_de_uso_no_se_propaga() {
        MuestrearInstancia casoQueFalla = instancia -> {
            throw new RuntimeException("ORA-12541: no se pudo conectar");
        };

        // Si esto se propagara, un fixedRate de Spring dejaría de reprogramar
        // el método para siempre -- el monitor dejaría de muestrear en silencio.
        assertThatCode(() -> new PlanificadorMuestreo(casoQueFalla, 1L).muestrear())
            .doesNotThrowAnyException();
    }
}
