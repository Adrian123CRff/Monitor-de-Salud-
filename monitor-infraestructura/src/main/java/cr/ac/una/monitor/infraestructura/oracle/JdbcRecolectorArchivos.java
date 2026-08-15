package cr.ac.una.monitor.infraestructura.oracle;

import cr.ac.una.monitor.aplicacion.puerto.salida.RecoleccionFallidaException;
import cr.ac.una.monitor.aplicacion.puerto.salida.RecolectorArchivos;
import cr.ac.una.monitor.dominio.modelo.Componente;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import cr.ac.una.monitor.dominio.modelo.Muestra;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee a1..a8 de V$DATAFILE / DBA_TABLESPACE_USAGE_METRICS / V$TEMPFILE /
 * V$LOG / V$LOGFILE (SQL de la skill
 * oracle-vistas-dinamicas/references/sql-archivos.md).
 *
 * "redundancia_redo" (variable de UmbralesIniciales) = min miembros por
 * grupo de redo (a6_min_miembros): 1 miembro = sin copia.
 *
 * recolectarTablespaces() trae el detalle fila por fila de
 * DBA_TABLESPACE_USAGE_METRICS (para MONITOR_TABLESPACE) -- recolectar()
 * solo trae el agregado (MAX/COUNT) que necesita CalculadorComponente.
 * used_space/tablespace_size vienen en bloques; se multiplican por
 * block_size para persistir bytes reales, verificado contra una instancia
 * Oracle viva (BLOCK_SIZE existe en la vista, no hay que asumirlo).
 */
@Component
public class JdbcRecolectorArchivos implements RecolectorArchivos {

    /** Umbral de "tablespace en riesgo" para el conteo a4_riesgo. Valor inicial, no calibrado. */
    private static final double UMBRAL_RIESGO_TABLESPACE_PCT = 90.0;

    private static final String SQL = """
        SELECT
            df.datafiles_online     AS a1,
            df.datafiles_offline    AS a2,
            df.datafiles_bytes      AS a3,
            ts.peor_tablespace_pct  AS a4,
            ts.tablespaces_riesgo   AS a4_riesgo,
            ts.peor_tablespace      AS a4_nombre,
            tf.tempfiles_online     AS a5,
            tf.tempfiles_bytes      AS a5_bytes,
            rl.grupos_redo          AS a6,
            rl.min_miembros_grupo   AS a6_min_miembros,
            lf.archivos_invalidos   AS a7,
            df.datafiles_recover    AS a8
        FROM
            ( SELECT
                COUNT(CASE WHEN status IN ('ONLINE','SYSTEM') THEN 1 END) AS datafiles_online,
                COUNT(CASE WHEN status IN ('OFFLINE','SYSOFF') THEN 1 END) AS datafiles_offline,
                COUNT(CASE WHEN status = 'RECOVER'            THEN 1 END) AS datafiles_recover,
                SUM(bytes)                                                AS datafiles_bytes
              FROM v$datafile
            ) df,
            ( SELECT
                ROUND(MAX(used_percent), 2)                            AS peor_tablespace_pct,
                COUNT(CASE WHEN used_percent > :umbral_riesgo THEN 1 END) AS tablespaces_riesgo,
                MAX(tablespace_name) KEEP (DENSE_RANK FIRST
                    ORDER BY used_percent DESC)                        AS peor_tablespace
              FROM dba_tablespace_usage_metrics
            ) ts,
            ( SELECT
                COUNT(CASE WHEN status = 'ONLINE' THEN 1 END) AS tempfiles_online,
                SUM(bytes)                                    AS tempfiles_bytes
              FROM v$tempfile
            ) tf,
            ( SELECT
                COUNT(DISTINCT group#) AS grupos_redo,
                MIN(members)           AS min_miembros_grupo
              FROM v$log
            ) rl,
            ( SELECT COUNT(CASE WHEN status = 'INVALID' THEN 1 END) AS archivos_invalidos
              FROM v$logfile
            ) lf
        """;

    private static final String SQL_TABLESPACES = """
        SELECT
            tablespace_name,
            used_percent,
            used_space * block_size      AS used_bytes,
            tablespace_size * block_size AS max_bytes
        FROM dba_tablespace_usage_metrics
        """;

    private final JdbcClient jdbc;

    public JdbcRecolectorArchivos(@Qualifier("oracleMonitoreado") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Muestra recolectar(InstanciaId instancia) {
        try {
            return jdbc.sql(SQL)
                .param("umbral_riesgo", UMBRAL_RIESGO_TABLESPACE_PCT)
                .query(this::mapear)
                .single();
        } catch (DataAccessException e) {
            throw new RecoleccionFallidaException(Componente.ARCHIVOS, instancia, e);
        }
    }

    @Override
    public List<DetalleTablespace> recolectarTablespaces(InstanciaId instancia) {
        try {
            return jdbc.sql(SQL_TABLESPACES).query(this::mapearTablespace).list();
        } catch (DataAccessException e) {
            throw new RecoleccionFallidaException(Componente.ARCHIVOS, instancia, e);
        }
    }

    private DetalleTablespace mapearTablespace(ResultSet rs, int rowNum) throws SQLException {
        return new DetalleTablespace(
            rs.getString("tablespace_name"),
            rs.getDouble("used_percent"),
            rs.getDouble("used_bytes"),
            rs.getDouble("max_bytes"));
    }

    private Muestra mapear(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Double> valores = new HashMap<>();

        valores.put("a1_datafiles_online", rs.getDouble("a1"));
        valores.put("a2_datafiles_offline", rs.getDouble("a2"));
        valores.put("a3_datafiles_bytes", rs.getDouble("a3"));
        double peorTablespacePct = rs.getDouble("a4");
        double minMiembrosGrupo = rs.getDouble("a6_min_miembros");
        // Se guarda dos veces a propósito: el nombre "a4_.../a6_..." es la columna
        // del esquema (crudo); "peor_tablespace_pct"/"redundancia_redo" es el nombre
        // que espera CalculadorComponente (ver catalogo-variables.md "derivadas").
        // Aquí no hay cómputo real -- son el mismo valor, dos claves.
        valores.put("a4_peor_tablespace_pct", peorTablespacePct);
        valores.put("peor_tablespace_pct", peorTablespacePct);
        valores.put("a4_tablespaces_riesgo", rs.getDouble("a4_riesgo"));
        valores.put("a5_tempfiles_online", rs.getDouble("a5"));
        valores.put("a5_tempfiles_bytes", rs.getDouble("a5_bytes"));
        valores.put("a6_grupos_redo", rs.getDouble("a6"));
        valores.put("a6_min_miembros_grupo", minMiembrosGrupo);
        valores.put("redundancia_redo", minMiembrosGrupo);
        valores.put("a7_archivos_invalidos", rs.getDouble("a7"));
        valores.put("a8_archivos_recover", rs.getDouble("a8"));

        return new Muestra(Componente.ARCHIVOS, Instant.now(), valores, false);
    }
}
