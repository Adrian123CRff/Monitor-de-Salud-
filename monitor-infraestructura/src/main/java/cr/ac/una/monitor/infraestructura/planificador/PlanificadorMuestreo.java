package cr.ac.una.monitor.infraestructura.planificador;

import cr.ac.una.monitor.aplicacion.puerto.entrada.MuestrearInstancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara MuestrearInstancia.ejecutar() automáticamente cada
 * monitor.muestreo.intervalo (decisión confirmada: un solo ciclo combinado,
 * no un @Scheduled por componente). MuestrearInstanciaServicio.ejecutar()
 * ya recolecta procesos (usuarios+fondo) + memoria + archivos juntos en un
 * único ciclo -- separar la frecuencia por componente exigiría refactorizar
 * el caso de uso para calcular el ISBD con datos parcialmente obsoletos de
 * cada uno (IP de hace 30s, IA de hace 10 min), que es peor que tener los
 * tres frescos del mismo instante.
 *
 * instancia-id fijo desde configuración, no RepositorioInstancias: coherente
 * con ADR 0001 (una sola instancia Oracle en Docker, no multi-tenant).
 *
 * ejecutar() ya no lanza RecoleccionFallidaException por componente (ver
 * MuestrearInstanciaServicio.recolectarSeguro) -- el try/catch de aquí es la
 * última red, para cualquier otro fallo inesperado: una excepción sin
 * atrapar dentro de un @Scheduled con fixedRate detiene las ejecuciones
 * futuras de ese método, y el monitor dejaría de muestrear en silencio.
 */
@Component
@ConditionalOnProperty(value = "monitor.planificador.habilitado", havingValue = "true", matchIfMissing = true)
public class PlanificadorMuestreo {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorMuestreo.class);

    private final MuestrearInstancia caso;
    private final InstanciaId instancia;

    public PlanificadorMuestreo(MuestrearInstancia caso, @Value("${monitor.instancia-id}") long instanciaId) {
        this.caso = caso;
        this.instancia = new InstanciaId(instanciaId);
    }

    @Scheduled(fixedRateString = "${monitor.muestreo.intervalo}")
    public void muestrear() {
        try {
            caso.ejecutar(instancia);
        } catch (Exception e) {
            log.warn("Fallo en el ciclo de muestreo de la instancia {}", instancia, e);
        }
    }
}
