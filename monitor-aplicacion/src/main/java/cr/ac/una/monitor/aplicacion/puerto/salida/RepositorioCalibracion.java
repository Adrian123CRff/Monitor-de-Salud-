package cr.ac.una.monitor.aplicacion.puerto.salida;

/** Pesos y umbrales vigentes (arrancan desde application.yml, recalibrables en caliente). Tipo Calibracion: pendiente en dominio.calibracion. */
public interface RepositorioCalibracion {

    Object vigente();

    void registrar(Object nueva);
}
