package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.modelo.Instancia;

import java.util.List;

/** El catálogo MONITOR_INSTANCIA (ADR 0001), solo lectura -- nada crea instancias todavía. */
public interface RepositorioInstancias {

    /** Excluye las inactivas (activa = false): así se marcan las filas que insertan los tests de integración. */
    List<Instancia> listarActivas();
}
