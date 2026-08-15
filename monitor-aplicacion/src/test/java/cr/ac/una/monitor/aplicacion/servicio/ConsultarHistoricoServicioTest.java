package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultarHistoricoServicioTest {

    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

    @Test
    void delega_el_rango_pedido_al_repositorio() {
        Isbd uno = new Isbd(Instant.parse("2026-01-01T00:00:00Z"), 82.0, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 82.0, Map.of())), Optional.empty(), Optional.empty(),
            false, List.of(), true);

        RepositorioIndices indicesFalso = new RepositorioIndices() {
            @Override
            public void guardar(InstanciaId instancia, Isbd isbd) { }

            @Override
            public Optional<Isbd> ultimo(InstanciaId instancia) {
                return Optional.empty();
            }

            @Override
            public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
                return List.of(uno);
            }
        };

        List<Isbd> resultado = new ConsultarHistoricoServicio(indicesFalso)
            .enRango(INSTANCIA, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(resultado).containsExactly(uno);
    }
}
