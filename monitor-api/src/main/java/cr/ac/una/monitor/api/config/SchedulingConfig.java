package cr.ac.una.monitor.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el procesamiento de @Scheduled -- ver PlanificadorMuestreo (monitor-infraestructura). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
