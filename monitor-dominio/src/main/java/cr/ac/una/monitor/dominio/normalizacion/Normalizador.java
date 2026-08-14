package cr.ac.una.monitor.dominio.normalizacion;

import java.util.List;

/**
 * Convierte un valor crudo (p1..p8, m1..m9, a1..a8 o sus derivadas) en una
 * puntuación de salud en [0, 100] donde 100 = sano. Ver skill
 * diseno-de-indicadores / references/normalizacion.md para el porqué de
 * cada función y la tabla de asignación por variable.
 */
public final class Normalizador {

    private Normalizador() {
    }

    /** Más alto es peor, con banda muerta antes de {@code ok}. El caso más común. */
    public static double linealInvertida(double valor, double ok, double critico) {
        if (critico <= ok) {
            throw new IllegalArgumentException(
                "critico debe ser mayor que ok en una métrica invertida");
        }
        if (valor <= ok) {
            return 100.0;
        }
        if (valor >= critico) {
            return 0.0;
        }
        return 100.0 * (critico - valor) / (critico - ok);
    }

    /** Más alto es mejor (p. ej. cache hit percentage). */
    public static double linealDirecta(double valor, double critico, double ok) {
        if (ok <= critico) {
            throw new IllegalArgumentException(
                "ok debe ser mayor que critico en una métrica directa");
        }
        if (valor >= ok) {
            return 100.0;
        }
        if (valor <= critico) {
            return 0.0;
        }
        return 100.0 * (valor - critico) / (ok - critico);
    }

    /** Respeta una clasificación por categorías ya establecida (NORMAL/ADVERTENCIA/ALTO/CRITICO). */
    public static double porTramos(double valor, List<Tramo> tramos) {
        for (Tramo t : tramos) {
            if (valor >= t.desde() && valor < t.hasta()) {
                double fraccion = (valor - t.desde()) / (t.hasta() - t.desde());
                return t.puntoDesde() + fraccion * (t.puntoHasta() - t.puntoDesde());
            }
        }
        return valor < tramos.get(0).desde() ? 100.0 : 0.0;
    }

    /** Eventos contables donde cada ocurrencia resta puntos (p. ej. bloqueos, Δ over-allocation). */
    public static double penalizacionDiscreta(int eventos, double puntosPorEvento, double piso) {
        return Math.max(piso, 100.0 - eventos * puntosPorEvento);
    }

    /** Eventos donde una sola ocurrencia ya es crítica (datafile offline, archivo inválido). */
    public static double criticoSiHayAlguno(int eventos) {
        return eventos > 0 ? 0.0 : 100.0;
    }
}
