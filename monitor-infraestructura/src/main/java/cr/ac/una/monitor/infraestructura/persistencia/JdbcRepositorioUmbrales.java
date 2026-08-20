package cr.ac.una.monitor.infraestructura.persistencia;

import cr.ac.una.monitor.aplicacion.puerto.salida.RepositorioUmbrales;
import cr.ac.una.monitor.dominio.calibracion.GrupoUmbral;
import cr.ac.una.monitor.dominio.calibracion.TipoUmbral;
import cr.ac.una.monitor.dominio.calibracion.Umbral;
import cr.ac.una.monitor.dominio.modelo.InstanciaId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee monitor_umbral_puntuacion (V8) resolviendo el perfil de la instancia.
 *
 * La herencia desde ESTANDAR es POR VARIABLE, no por perfil completo: el
 * DISTINCT ON se queda con la fila del perfil propio cuando existe y cae a
 * la de ESTANDAR cuando no. Así una instancia PEQUENA puede redefinir solo
 * util_procesos_pct y heredar el resto -- que es como se ve una calibración
 * por tamaño real, donde los límites duros (datafile OFFLINE) no cambian
 * nunca y los porcentajes de utilización sí.
 *
 * Una instancia inexistente, o una tabla vacía, devuelven un mapa vacío en
 * vez de reventar: MuestrearInstanciaServicio cae a UmbralesIniciales, mismo
 * patrón que calibracionVigenteOInicial().
 */
@Repository
public class JdbcRepositorioUmbrales implements RepositorioUmbrales {

    private final JdbcClient jdbc;

    public JdbcRepositorioUmbrales(@Qualifier("historico") DataSource ds) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Map<GrupoUmbral, List<Umbral>> vigentes(InstanciaId instancia) {
        List<Fila> filas = jdbc.sql("""
                WITH perfil_instancia AS (
                    SELECT perfil FROM monitor_instancia WHERE id = :instancia
                )
                SELECT DISTINCT ON (u.grupo, u.variable)
                       u.grupo, u.variable, u.tipo, u.valor_ok, u.valor_critico,
                       u.puntos_por_evento, u.peso_en_componente,
                       u.veto_absoluto, u.veto_si_valor_supera
                FROM monitor_umbral_puntuacion u
                CROSS JOIN perfil_instancia p
                WHERE u.perfil = p.perfil OR u.perfil = 'ESTANDAR'
                ORDER BY u.grupo, u.variable, (u.perfil = p.perfil) DESC
                """)
            .param("instancia", instancia.valor())
            .query(this::mapear)
            .list();

        Map<GrupoUmbral, List<Umbral>> porGrupo = new EnumMap<>(GrupoUmbral.class);
        for (Fila fila : filas) {
            porGrupo.computeIfAbsent(fila.grupo(), g -> new ArrayList<>()).add(fila.umbral());
        }
        return porGrupo;
    }

    private Fila mapear(ResultSet rs, int rowNum) throws SQLException {
        // getDouble devuelve 0.0 tanto para un 0 real como para NULL -- hace
        // falta wasNull() para no confundir "sin veto por valor" con "veto en 0".
        double vetoSupera = rs.getDouble("veto_si_valor_supera");
        Optional<Double> vetoSiValorSupera = rs.wasNull() ? Optional.empty() : Optional.of(vetoSupera);

        Umbral umbral = new Umbral(
            rs.getString("variable"),
            TipoUmbral.valueOf(rs.getString("tipo")),
            rs.getDouble("valor_ok"),
            rs.getDouble("valor_critico"),
            rs.getDouble("puntos_por_evento"),
            rs.getDouble("peso_en_componente"),
            rs.getBoolean("veto_absoluto"),
            vetoSiValorSupera);

        return new Fila(GrupoUmbral.valueOf(rs.getString("grupo")), umbral);
    }

    /** El grupo no cabe en Umbral (es la clave de agrupación, no un atributo del umbral). */
    private record Fila(GrupoUmbral grupo, Umbral umbral) {
    }
}
