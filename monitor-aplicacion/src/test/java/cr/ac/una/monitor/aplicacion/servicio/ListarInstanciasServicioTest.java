package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioIndices;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioInstancias;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Estado;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.Instancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.InstanciaResumen;
import cr.ac.una.monitor.dominio.modelo.Isbd;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ListarInstanciasServicioTest {

    private static final Instancia PRINCIPAL = new Instancia(new InstanciaId(1L), "principal");
    private static final Instancia SIN_DATOS = new Instancia(new InstanciaId(2L), "recien-agregada");

    @Test
    void combina_cada_instancia_del_catalogo_con_su_ultimo_isbd() {
        Isbd isbd = new Isbd(Instant.now(), 90.0, Estado.OPTIMO,
            Optional.of(new Indicador(Componente.PROCESOS, 90.0, Map.of())),
            Optional.of(new Indicador(Componente.MEMORIA, 90.0, Map.of())),
            Optional.of(new Indicador(Componente.ARCHIVOS, 90.0, Map.of())),
            false, List.of(), false);

        RepositorioInstancias instanciasFalso = () -> List.of(PRINCIPAL);
        RepositorioIndices indicesFalso = new RepositorioIndices() {
            @Override
            public void guardar(InstanciaId instancia, Isbd isbd) { }

            @Override
            public Optional<Isbd> ultimo(InstanciaId instancia) {
                return instancia.equals(PRINCIPAL.id()) ? Optional.of(isbd) : Optional.empty();
            }

            @Override
            public List<Isbd> enRango(InstanciaId instancia, Instant desde, Instant hasta) {
                return List.of();
            }
        };

        List<InstanciaResumen> resultado = new ListarInstanciasServicio(instanciasFalso, indicesFalso).listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).instancia()).isEqualTo(PRINCIPAL);
        assertThat(resultado.get(0).salud()).contains(isbd);
    }

    @Test
    void una_instancia_sin_ningun_muestreo_todavia_queda_con_salud_vacio_no_con_error() {
        RepositorioInstancias instanciasFalso = () -> List.of(SIN_DATOS);
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

        List<InstanciaResumen> resultado = new ListarInstanciasServicio(instanciasFalso, indicesVacio).listar();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).salud()).isEmpty();
    }
}
