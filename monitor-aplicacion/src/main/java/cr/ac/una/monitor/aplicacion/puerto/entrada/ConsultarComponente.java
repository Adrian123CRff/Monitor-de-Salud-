package cr.ac.una.monitor.aplicacion.puerto.entrada;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * El detalle de un componente: no solo el dato crudo, sino cuánto puntuó
 * cada variable y por lo tanto cuál está tirando el indicador hacia abajo.
 *
 * Es el flujo que el profesor describe como el centro del producto (notas de
 * clase): "si un cliente aparece en rojo, se puede entrar para ver cuál de
 * las variables específicas está fuera de los límites normales". Hasta ahora
 * el drill-down mostraba `a6_min_miembros_grupo: 1.0` sin decir que eso
 * puntuaba 0 y le costaba 10 puntos al componente.
 *
 * Las puntuaciones se RECALCULAN al consultar, contra los umbrales vigentes
 * hoy -- no se leen de ningún lado, porque MONITOR_INDICES solo guarda el
 * agregado (IP/IM/IA) y el desglose por variable nunca se persistió. La
 * consecuencia hay que tenerla presente: si alguien recalibra un umbral, el
 * detalle de una muestra vieja se muestra con la escala NUEVA, no con la que
 * estaba vigente cuando se tomó. Para responder "¿por qué está así ahora?"
 * eso es lo correcto; para auditar "¿con qué umbral se calculó aquel día?"
 * haría falta persistir el desglose, que es un cambio de esquema y todavía
 * no hace falta.
 */
public interface ConsultarComponente {

    /**
     * Clave -> vista. PROCESOS devuelve dos ("usuarios" y "fondo", ver ADR
     * 0006: no hay una única "última muestra de PROCESOS"); memoria y
     * archivos devuelven una sola, "actual".
     *
     * Devuelve un mapa VACÍO si la instancia no tiene ninguna muestra de ese
     * componente -- traducir eso a un 404 es decisión del borde HTTP
     * (ComponentesController/SinDatosException), no de la aplicación.
     */
    Map<String, VistaComponente> detalle(InstanciaId instancia, Componente componente);

    /**
     * puntuacion/vetado quedan en null cuando no se pudo puntuar la muestra
     * (por ejemplo, una primera muestra de fondo donde todas las variables
     * con umbral son deltas que aún no existen). El detalle crudo se muestra
     * igual: es mejor que la pantalla explique menos a que reviente.
     */
    record VistaComponente(
            Componente componente,
            Instant momento,
            Map<String, Double> valores,
            Double puntuacion,
            Boolean vetado,
            List<VariableEvaluada> variables) {
    }

    /**
     * Una variable que SÍ puntúa, con su aporte. Las variables de contexto
     * (recolectadas pero sin umbral) no aparecen aquí -- siguen estando en
     * `valores`, que es el crudo completo.
     *
     * aportePerdido = (100 - puntuacion) * peso: cuántos puntos del
     * componente se está llevando esta variable. Es lo que permite ordenar
     * el detalle por "qué me está costando más" en vez de alfabéticamente.
     */
    record VariableEvaluada(
            String variable,
            Double valor,
            double puntuacion,
            double pesoEnComponente,
            boolean disparoVeto) {

        public double aportePerdido() {
            return (100.0 - puntuacion) * pesoEnComponente;
        }
    }
}
