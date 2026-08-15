package cr.ac.una.monitor.dominio.alertas;

import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.time.Instant;
import java.util.Optional;

/**
 * Un episodio de alerta con duración, no un evento puntual (ver skill
 * diseno-de-indicadores / references/calibracion.md, "Parte 3 -- Ciclo de
 * vida de las alertas"): sin esto, una condición sostenida seis horas
 * genera cientos de filas idénticas.
 *
 * La clave de deduplicación es (instancia, variable, entidad) -- entidad es
 * Optional porque la mayoría de variables no la necesitan (p6_sesiones_bloqueadas
 * es una sola por instancia), pero dos tablespaces distintos al 93% son dos
 * alertas, no una: entidad = 'USERS' vs 'SYSTEM' es lo que las distingue.
 *
 * id es null antes de guardar (lo asigna RepositorioAlertas.abrir()).
 */
public record Alerta(
        Long id,
        InstanciaId instancia,
        Componente componente,
        String variable,
        Optional<String> entidad,
        Nivel nivel,
        double valor,
        double umbral,
        String descripcion,
        Instant abiertaEn,
        Optional<Instant> cerradaEn) {

    public Alerta {
        if (nivel == Nivel.NORMAL) {
            throw new IllegalArgumentException("Una Alerta no puede tener nivel NORMAL -- eso significa 'cerrada'.");
        }
    }

    public boolean abierta() {
        return cerradaEn.isEmpty();
    }

    public Alerta cerrar(Instant momento) {
        return new Alerta(id, instancia, componente, variable, entidad, nivel, valor, umbral, descripcion,
            abiertaEn, Optional.of(momento));
    }
}
