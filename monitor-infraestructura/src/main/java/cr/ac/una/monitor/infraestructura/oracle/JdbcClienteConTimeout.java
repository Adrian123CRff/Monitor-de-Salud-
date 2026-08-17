package cr.ac.una.monitor.infraestructura.oracle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

/**
 * JdbcClient.create(DataSource) no expone queryTimeout -- hay que construir
 * el JdbcTemplate aparte para poder fijarlo, y envolverlo después
 * (JdbcClient.create(JdbcTemplate) sí existe). Sin esto, ninguna consulta
 * de los recolectores tenía límite de tiempo: si Oracle entra en un
 * library cache lock o el host swapea, el hilo del planificador (uno solo,
 * ver SchedulingConfig/application.yml spring.task.scheduling.pool-size)
 * queda bloqueado para siempre -- el monitor deja de muestrear en
 * silencio, justo el modo de fallo que un monitor de salud no puede tener.
 * Encontrado por auditoría externa (ver docs/), verificado: cero
 * coincidencias de "timeout" en todo monitor-infraestructura antes de esto.
 *
 * 10s es generoso para las consultas de este proyecto (agregados sobre
 * V$ con pocas filas, verificado en vivo en fracciones de segundo) y deja
 * margen de sobra dentro del intervalo de muestreo de 60s.
 */
final class JdbcClienteConTimeout {

    private static final int SEGUNDOS = 10;

    private JdbcClienteConTimeout() {
    }

    static JdbcClient crear(DataSource ds) {
        JdbcTemplate plantilla = new JdbcTemplate(ds);
        plantilla.setQueryTimeout(SEGUNDOS);
        return JdbcClient.create(plantilla);
    }
}
