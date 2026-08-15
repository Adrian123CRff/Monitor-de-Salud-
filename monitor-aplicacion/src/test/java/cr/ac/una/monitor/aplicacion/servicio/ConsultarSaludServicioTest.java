package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.SaludNoDisponibleException;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ConsultarSaludServicioTest {

    private static final InstanciaId INSTANCIA = new InstanciaId(1L);

    @Test
    void devuelve_el_ultimo_isbd_guardado() {
        Isbd guardado = new Isbd(Instant.now(), 82.0, Estado.SALUDABLE,
            Optional.of(new Indicador(Componente.PROCESOS, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 82.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 82.0, Map.of())),
            false, List.of(), false);

        RepositorioIndices indicesFalso = new RepositorioIndices() {
            @Override
            public void guardar(InstanciaId instancia, Isbd isbd) { }

            @Override
            public Optional<Isbd> ultimo(InstanciaId instancia) {
                return Optional.of(guardado);
            }

            @Override
            public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
                return List.of();
            }
        };

        Isbd resultado = new ConsultarSaludServicio(indicesFalso).actual(INSTANCIA);

        assertThat(resultado).isSameAs(guardado);
    }

    @Test
    void sin_ningun_isbd_guardado_lanza_saludNoDisponible() {
        RepositorioIndices indicesVacio = new RepositorioIndices() {
            @Override
            public void guardar(InstanciaId instancia, Isbd isbd) { }

            @Override
            public Optional<Isbd> ultimo(InstanciaId instancia) {
                return Optional.empty();
            }

            @Override
            public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
                return List.of();
            }
        };

        assertThatExceptionOfType(SaludNoDisponibleException.class)
            .isThrownBy(() -> new ConsultarSaludServicio(indicesVacio).actual(INSTANCIA));
    }
}
