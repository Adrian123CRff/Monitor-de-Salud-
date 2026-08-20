package cr.ac.una.monitor.api.dto;

import cr.ac.una.monitor.dominio.modelo.InstanciaResumen;

import java.time.Duration;

/**
 * DTO de un tile de la vista general (GET /api/v1/instancias). salud queda
 * null cuando la instancia todavía no tiene ningún Isbd calculado (catálogo
 * recién agregado, ver InstanciaResumen) -- el frontend lo distingue de un
 * estado real para mostrar "sin datos todavía" en vez de un semáforo falso.
 */
public record ResumenInstanciaDto(long id, String alias, IsbdDto salud) {

    public static ResumenInstanciaDto desde(InstanciaResumen resumen, Duration intervaloMuestreo) {
        IsbdDto salud = resumen.salud()
            .map(isbd -> IsbdDto.desde(isbd, CalculadorVetustez.esVetusto(isbd.momento(), intervaloMuestreo)))
            .orElse(null);
        return new ResumenInstanciaDto(resumen.instancia().id().valor(), resumen.instancia().alias(), salud);
    }
}
