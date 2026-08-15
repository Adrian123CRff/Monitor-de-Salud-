package cr.ac.una.monitor.dominio.alertas;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decide qué hacer con el episodio de alerta de una variable, dado el nivel
 * ya calculado con histéresis/confirmación (ver EvaluadorNivel/ConfirmadorTemporal)
 * y la alerta que estuviera abierta. Deduplicación: "misma alerta, mismo
 * nivel: no hacer nada" es la rama que hace legible el panel (ver skill
 * diseno-de-indicadores / references/calibracion.md, "Parte 3").
 */
public final class MotorAlertas {

    public ResultadoEvaluacion evaluar(Nivel nivelNuevo, Optional<Alerta> abierta, Supplier<Alerta> construirNueva) {
        if (nivelNuevo == Nivel.NORMAL) {
            return abierta.<ResultadoEvaluacion>map(ResultadoEvaluacion.Cerrar::new)
                .orElseGet(ResultadoEvaluacion.SinCambios::new);
        }
        if (abierta.isEmpty()) {
            return new ResultadoEvaluacion.Abrir(construirNueva.get());
        }
        if (abierta.get().nivel() != nivelNuevo) {
            return new ResultadoEvaluacion.CerrarYAbrir(abierta.get(), construirNueva.get());
        }
        return new ResultadoEvaluacion.SinCambios();
    }
}
