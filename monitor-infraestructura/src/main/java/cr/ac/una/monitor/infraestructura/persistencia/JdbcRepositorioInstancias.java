package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioInstancias;
import cr.ac.una.monitor.dominio.modelo.Instancia;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Catálogo MONITOR_INSTANCIA (V1), solo lectura -- ver RepositorioInstancias. */
@Repository
public class JdbcRepositorioInstancias implements RepositorioInstancias {

    private final JdbcClient jdbc;

    public JdbcRepositorioInstancias(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public List<Instancia> listarActivas() {
        return jdbc.sql("""
                SELECT id, alias
                FROM monitor_instancia
                WHERE activa = true
                ORDER BY id
                """)
            .query(this::mapear)
            .list();
    }

    private Instancia mapear(ResultSet rs, int rowNum) throws SQLException {
        return new Instancia(new InstanciaId(rs.getLong("id")), rs.getString("alias"));
    }
}
