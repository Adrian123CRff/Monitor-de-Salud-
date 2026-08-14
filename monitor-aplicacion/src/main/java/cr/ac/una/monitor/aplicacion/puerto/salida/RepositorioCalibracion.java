package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.calibracion.Calibracion;

/** Pesos y umbrales vigentes (arrancan desde application.yml, recalibrables en caliente). */
public interface RepositorioCalibracion {

    Calibracion vigente();

    void registrar(Calibracion nueva);
}
