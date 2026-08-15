package cr.ac.una.monitor.dominio.alertas;

/**
 * Qué hacer con el episodio de alerta tras evaluar una muestra. MotorAlertas
 * decide, no ejecuta -- igual que MotorIndicadores/CalculadorComponente,
 * quien orquesta (MuestrearInstanciaServicio) es quien llama a
 * RepositorioAlertas según este resultado.
 */
public sealed interface ResultadoEvaluacion {

    /** Mismo nivel que la alerta ya abierta, o NORMAL sin alerta previa: no hay nada que hacer. */
    record SinCambios() implements ResultadoEvaluacion {
    }

    /** No había alerta abierta y la condición pasó a ADVERTENCIA/ALTO/CRITICO. */
    record Abrir(Alerta nueva) implements ResultadoEvaluacion {
    }

    /** Había una alerta abierta y la condición volvió a NORMAL. */
    record Cerrar(Alerta existente) implements ResultadoEvaluacion {
    }

    /**
     * Escaló o bajó de nivel sin llegar a NORMAL: se cierra la anterior y se
     * abre una nueva con el nivel actual, para que el histórico conserve la
     * progresión del episodio en vez de mutar la fila abierta.
     */
    record CerrarYAbrir(Alerta aCerrar, Alerta aAbrir) implements ResultadoEvaluacion {
    }
}
