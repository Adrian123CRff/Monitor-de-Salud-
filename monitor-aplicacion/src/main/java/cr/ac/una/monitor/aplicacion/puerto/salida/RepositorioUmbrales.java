package cr.ac.una.monitor.aplicacion.puerto.salida;

import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;

import java.util.List;
import java.util.Map;

/**
 * Lee los umbrales de puntuación vigentes para una instancia.
 *
 * Deliberadamente separado de RepositorioCalibracion: monitor_calibracion
 * versiona los PESOS de IP/IM/IA y el veto (y cada recalibración de pesos
 * abre una fila nueva, cerrando la anterior), mientras que los umbrales por
 * variable son un eje de calibración independiente -- cambiar el peso de
 * memoria no debería invalidar el umbral de pga_uso_pct. Por eso los
 * umbrales NO cuelgan de calibracion_id, a diferencia de lo que asumía el
 * esquema de V1 (ver V8 y ADR 0007).
 *
 * La resolución del perfil (PerfilInstancia, requisito "paramétrico" de las
 * notas de clase) queda del lado del adaptador: quien llama pide "los
 * umbrales de esta instancia" y no necesita saber si es PEQUENA o GRANDE.
 */
public interface RepositorioUmbrales {

    /**
     * Los umbrales vigentes de cada grupo para esta instancia, resolviendo su
     * perfil y heredando de ESTANDAR variable por variable (ver
     * PerfilInstancia).
     *
     * Devuelve un mapa vacío -- no una excepción -- si no hay nada
     * configurado: quien llama decide el respaldo (ver
     * MuestrearInstanciaServicio, que cae a UmbralesIniciales). Un grupo
     * puede faltar del mapa aunque otros estén presentes.
     */
    Map<GrupoUmbral, List<Umbral>> vigentes(InstanciaId instancia);
}
