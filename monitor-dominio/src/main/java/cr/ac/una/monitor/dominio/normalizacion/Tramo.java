package cr.ac.una.monitor.dominio.normalizacion;

/** Un tramo de la clasificación NORMAL/ADVERTENCIA/ALTO/CRITICO, con su rango de puntuación. */
public record Tramo(double desde, double hasta, double puntoDesde, double puntoHasta) {
}
