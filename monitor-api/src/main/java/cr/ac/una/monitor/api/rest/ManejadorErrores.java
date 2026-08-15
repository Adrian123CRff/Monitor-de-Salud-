package cr.ac.una.monitor.api.rest;

import cr.ac.una.monitor.aplicacion.puerto.entrada.SaludNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores con formato uniforme (RFC 9457, ver skill de arquitectura) -- todos
 * los endpoints devuelven la misma forma de error, sin exponer stack traces.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(SaludNoDisponibleException.class)
    public ProblemDetail saludNoDisponible(SaludNoDisponibleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SinDatosException.class)
    public ProblemDetail sinDatos(SinDatosException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Componente inválido en la URL ({c} que no es PROCESOS|MEMORIA|ARCHIVOS), pesos que no suman 1, etc. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** P. ej. MotorIndicadores cuando fallan los tres componentes en el mismo ciclo -- no hay nada que calcular. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail estadoInvalido(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
