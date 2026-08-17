package cr.ac.una.monitor.infraestructura.oracle;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.monitor.dominio.modelo.DetalleTablespace;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver JdbcRecolectorProcesosIT: requiere `docker compose up -d` primero.
 * recolectarTablespaces() es el que alimenta MONITOR_TABLESPACE (ver
 * JdbcRepositorioTablespaces) -- distinto del agregado que trae recolectar().
 */
class JdbcRecolectorArchivosTablespacesIT {

    private static HikariDataSource dataSource;

    @BeforeAll
    static void crearDataSource() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        dataSource.setUsername("c##monitor");
        dataSource.setPassword("MonitorPass123");
        dataSource.setMaximumPoolSize(2);
        dataSource.setReadOnly(true);
    }

    @AfterAll
    static void cerrarDataSource() {
        dataSource.close();
    }

    /**
     * Guarda de regresión directa: la instancia se conectó una vez al servicio
     * FREE (CDB$ROOT) en vez de FREEPDB1 (el PDB) -- DBA_TABLESPACE_USAGE_METRICS
     * no da error ahí, solo devuelve los tablespaces del contenedor equivocado, y
     * este mismo test pasaba en verde estando ciego a FREEPDB1 (ver skill
     * oracle-vistas-dinamicas/references/cdb-pdb.md, "Dónde estoy conectado").
     * Este assert falla ruidosamente si el connection string vuelve a apuntar
     * a la raíz en vez del PDB.
     */
    @Test
    void esta_conectado_al_pdb_no_al_cdb_root() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT SYS_CONTEXT('USERENV','CON_NAME') AS con_name FROM dual")) {
            rs.next();
            assertThat(rs.getString("con_name")).isEqualTo("FREEPDB1");
        }
    }

    @Test
    void recolecta_el_detalle_de_todos_los_tablespaces_de_la_instancia_docker() {
        JdbcRecolectorArchivos recolector = new JdbcRecolectorArchivos(dataSource);

        List<DetalleTablespace> detalle = recolector.recolectarTablespaces(new InstanciaId(1L));

        // SYSTEM y SYSAUX siempre existen en una instancia Oracle recién levantada.
        assertThat(detalle).extracting(DetalleTablespace::nombre).contains("SYSTEM", "SYSAUX");
        assertThat(detalle).allSatisfy(ts -> {
            assertThat(ts.usedPercent()).isBetween(0.0, 100.0);
            assertThat(ts.maxBytes()).isGreaterThan(0.0);
        });
    }
}
