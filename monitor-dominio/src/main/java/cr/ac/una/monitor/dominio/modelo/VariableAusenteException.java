package cr.ac.una.monitor.dominio.modelo;

/** Una muestra no trae una variable que el cálculo de indicadores esperaba. */
public class VariableAusenteException extends RuntimeException {

    public VariableAusenteException(Componente componente, String variable) {
        super("Falta la variable '" + variable + "' en la muestra de " + componente);
    }
}
