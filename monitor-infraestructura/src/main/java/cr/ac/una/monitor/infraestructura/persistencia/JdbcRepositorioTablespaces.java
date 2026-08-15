package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioTablespaces;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Persistencia del histórico crudo de detalle por tablespace (MONITOR_TABLESPACE, V1). */
@Repository
public class JdbcRepositorioTablespaces implements RepositorioTablespaces {

    private final JdbcClient jdbc;

    public JdbcRepositorioTablespaces(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public void guardar(InstanciaId instancia, Instant momento, List<DetalleTablespace> detalle) {
        OffsetDateTime muestreadoEn = OffsetDateTime.ofInstant(momento, ZoneOffset.UTC);
        for (DetalleTablespace ts : detalle) {
            jdbc.sql("""
                    INSERT INTO monitor_tablespace
                        (instancia_id, muestreado_en, tablespace_name, used_percent, used_bytes, max_bytes)
                    VALUES (:instancia_id, :muestreado_en, :tablespace_name, :used_percent, :used_bytes, :max_bytes)
                    """)
                .param("instancia_id", instancia.valor())
                .param("muestreado_en", muestreadoEn)
                .param("tablespace_name", ts.nombre())
                .param("used_percent", ts.usedPercent())
                .param("used_bytes", ts.usedBytes())
                .param("max_bytes", ts.maxBytes())
                .update();
        }
    }
}
