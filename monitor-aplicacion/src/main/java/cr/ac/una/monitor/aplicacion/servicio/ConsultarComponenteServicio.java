package cr.ac.una.monitor.aplicacion.servicio;

import cr.ac.una.monitor.aplicacion.puerto.entrada.ConsultarComponente;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestras;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioMuestrasFondo;
import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioUmbrales;
import cr.ac.una.monitor.dominio.agregacion.CalculadorComponente;
import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.calibracion.UmbralesIniciales;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.Indicador;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Combina la última muestra cruda de un componente con la puntuación que
 * cada una de sus variables produce hoy, para responder la pregunta que el
 * dato crudo por sí solo no responde: cuál variable está tirando el
 * indicador hacia abajo.
 *
 * Antes esto no existía y ComponentesController leía los repositorios
 * directamente -- correcto mientras la respuesta era "lo último guardado,
 * tal cual". Ahora hay una decisión de negocio (qué umbrales aplicar, cómo
 * ordenar el aporte, qué hacer si no se puede puntuar), así que baja a un
 * caso de uso.
 *
 * Reutiliza CalculadorComponente, el MISMO calculador que usa el muestreo
 * -- no una copia de la fórmula. Si la puntuación que muestra el detalle no
 * coincidiera con la que produjo el ISBD, sería un síntoma de umbrales
 * recalibrados entre una cosa y otra, no de dos implementaciones distintas.
 */
@Service
public class ConsultarComponenteServicio implements ConsultarComponente {

    private static final Logger log = LoggerFactory.getLogger(ConsultarComponenteServicio.class);

    private final RepositorioMuestras muestras;
    private final RepositorioMuestrasFondo muestrasFondo;
    private final RepositorioUmbrales umbrales;
    private final CalculadorComponente calculador = new CalculadorComponente();

    public ConsultarComponenteServicio(RepositorioMuestras muestras, RepositorioMuestrasFondo muestrasFondo,
            RepositorioUmbrales umbrales) {
        this.muestras = muestras;
        this.muestrasFondo = muestrasFondo;
        this.umbrales = umbrales;
    }

    @Override
    public Map<String, VistaComponente> detalle(InstanciaId instancia, Componente componente) {
        Map<GrupoUmbral, List<Umbral>> vigentes = umbrales.vigentes(instancia);
        Map<String, VistaComponente> resultado = new LinkedHashMap<>();

        // PROCESOS se puntúa en dos pasadas y se guarda en dos tablas (ADR 0006),
        // así que su detalle son dos vistas; los otros dos componentes, una.
        String claveAgregado = componente == Componente.PROCESOS ? "usuarios" : "actual";
        GrupoUmbral grupoAgregado = componente == Componente.PROCESOS
            ? GrupoUmbral.PROCESOS_USUARIOS
            : GrupoUmbral.valueOf(componente.name());

        muestras.ultima(instancia, componente)
            .ifPresent(m -> resultado.put(claveAgregado, vistaDe(m, componente, grupoAgregado, vigentes)));

        if (componente == Componente.PROCESOS) {
            muestrasFondo.ultima(instancia)
                .ifPresent(m -> resultado.put("fondo",
                    vistaDe(m, componente, GrupoUmbral.PROCESOS_FONDO, vigentes)));
        }

        return resultado;
    }

    private VistaComponente vistaDe(Muestra muestra, Componente componente, GrupoUmbral grupo,
            Map<GrupoUmbral, List<Umbral>> vigentes) {
        List<Umbral> delGrupo = vigentes.get(grupo);
        if (delGrupo == null || delGrupo.isEmpty()) {
            delGrupo = UmbralesIniciales.porGrupo().get(grupo);
        }

        Optional<Indicador> indicador = puntuarSeguro(muestra, componente, delGrupo, grupo);
        if (indicador.isEmpty()) {
            return new VistaComponente(componente, muestra.momento(), muestra.valores(), null, null, List.of());
        }

        Map<String, Double> puntuaciones = indicador.get().puntuacionesPorVariable();
        List<VariableEvaluada> variables = new ArrayList<>();
        for (Umbral u : delGrupo) {
            Double puntuacion = puntuaciones.get(u.variable());
            if (puntuacion == null) {
                continue; // la muestra no trae esa variable este ciclo (p. ej. una delta sin historial)
            }
            variables.add(new VariableEvaluada(
                u.variable(),
                muestra.valores().get(u.variable()),
                puntuacion,
                u.pesoEnComponente(),
                disparoVeto(u, muestra.valores().get(u.variable()), puntuacion),
                u.tipo(),
                u.valorOk(),
                u.valorCritico()));
        }

        // Peor aporte primero: la pantalla debe abrir por lo que está costando
        // puntos, no por orden alfabético ni por el orden en que se declararon
        // los umbrales.
        variables.sort(Comparator.comparingDouble(VariableEvaluada::aportePerdido).reversed());

        return new VistaComponente(componente, muestra.momento(), muestra.valores(),
            indicador.get().puntuacion(), indicador.get().vetado(), List.copyOf(variables));
    }

    /**
     * Una muestra donde ninguna variable coincide con los umbrales configurados
     * hace que CalculadorComponente lance IllegalStateException. En el muestreo
     * eso es correcto (algo está mal configurado y hay que enterarse), pero en
     * una pantalla de consulta significaría un 503 en vez de mostrar el crudo
     * que sí tenemos.
     */
    private Optional<Indicador> puntuarSeguro(Muestra muestra, Componente componente, List<Umbral> umbralesDelGrupo,
            GrupoUmbral grupo) {
        try {
            return Optional.of(calculador.calcular(muestra, componente, umbralesDelGrupo));
        } catch (IllegalStateException e) {
            log.warn("No se pudo puntuar el detalle de {}: {}", grupo, e.getMessage());
            return Optional.empty();
        }
    }

    /** Ver Umbral: dos mecanismos distintos, y solo uno depende del valor crudo. */
    private boolean disparoVeto(Umbral u, Double crudo, double puntuacion) {
        if (u.vetoAbsoluto() && puntuacion <= 0.0) {
            return true;
        }
        return crudo != null && u.vetoSiValorSupera().isPresent() && crudo >= u.vetoSiValorSupera().get();
    }
}
