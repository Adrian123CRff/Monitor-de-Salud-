# Estructura del backend

## POM padre

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
  </parent>

  <groupId>cr.ac.una</groupId>
  <artifactId>monitor-salud-oracle</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <archunit.version>1.4.1</archunit.version>
  </properties>

  <modules>
    <module>monitor-dominio</module>
    <module>monitor-aplicacion</module>
    <module>monitor-infraestructura</module>
    <module>monitor-api</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <!-- El BOM de Oracle alinea ojdbc con sus artefactos relacionados
           (ucp, oraclepki, osdt_core...). Fijar versiones a mano es la
           receta clásica del NoClassDefFoundError en tiempo de ejecución. -->
      <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc-bom</artifactId>
        <version>23.26.3.0.0</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

## monitor-dominio: cero dependencias

Este `pom.xml` es la parte más importante de toda la estructura, precisamente por
lo que **no** tiene:

```xml
<project>
  <parent>
    <groupId>cr.ac.una</groupId>
    <artifactId>monitor-salud-oracle</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>monitor-dominio</artifactId>

  <dependencies>
    <!-- Solo test. En main: Java puro. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Sin `spring-boot-starter`, sin JPA, sin Jackson, sin driver. La regla de dependencia
deja de ser una convención y pasa a ser un hecho verificado por el compilador.

### Modelo del dominio

Records de Java para los valores inmutables — que es lo que son las muestras y los
indicadores: hechos ocurridos en un instante, que nadie debería poder mutar después.

```java
package cr.ac.una.monitor.dominio.modelo;

import java.time.Instant;
import java.util.Map;

/** Una lectura cruda de un subsistema en un instante. */
public record Muestra(
        Componente componente,
        Instant momento,
        Map<String, Double> valores,     // "p1" -> 180.0
        boolean instanciaReiniciada) {

    public double valor(String variable) {
        Double v = valores.get(variable);
        if (v == null) {
            throw new VariableAusenteException(componente, variable);
        }
        return v;
    }
}

public enum Componente { PROCESOS, MEMORIA, ARCHIVOS }

/** Puntuación de salud 0-100 de un componente. 100 = sano. */
public record Indicador(Componente componente, double puntuacion,
                        Map<String, Double> puntuacionesPorVariable) {
    public Indicador {
        if (puntuacion < 0 || puntuacion > 100) {
            throw new IllegalArgumentException(
                "Un indicador es una puntuación de salud en [0,100], recibido: " + puntuacion
                + ". Si venía de una utilización, falta invertir la polaridad.");
        }
    }
}

/** El índice global, con su estado y las causas que lo determinaron. */
public record Isbd(Instant momento, double puntuacion, Estado estado,
                   Indicador ip, Indicador im, Indicador ia,
                   boolean estadoPorVeto, List<String> causas) { }

public enum Estado {
    OPTIMO(90, 100), SALUDABLE(75, 90), ADVERTENCIA(60, 75),
    DEGRADADO(40, 60), CRITICO(0, 40);

    private final double min, max;
    Estado(double min, double max) { this.min = min; this.max = max; }

    public static Estado desdePuntuacion(double p) {
        for (Estado e : values()) {
            if (p >= e.min && (p < e.max || e == OPTIMO)) return e;
        }
        throw new IllegalArgumentException("Puntuación fuera de [0,100]: " + p);
    }
}
```

El `throw` en el constructor compacto de `Indicador` parece defensivo de más y es
la red que atrapa el error de polaridad. Si algún adaptador pasa una utilización sin
invertir, el sistema falla ruidosamente en el punto exacto del error, en vez de
producir un ISBD plausible y equivocado durante semanas. Los errores silenciosos son
los caros; este mensaje incluso dice qué revisar.

## monitor-aplicacion: puertos

Los puertos son interfaces definidas **desde la perspectiva del dominio**. Su
vocabulario es el del negocio, no el de la tecnología: `RecolectorProcesos`, no
`OracleJdbcDao`. Ese detalle de nombrado es lo que mantiene la inversión de
dependencias real y no solo formal.

```java
package cr.ac.una.monitor.aplicacion.puerto.salida;

public interface RecolectorProcesos {
    /**
     * @throws RecoleccionFallidaException si no se pudo leer la instancia.
     *         El fallo es un dato de salud, no solo un error técnico:
     *         quien llame debe registrarlo, no tragárselo.
     */
    Muestra recolectar(InstanciaId instancia);
}

public interface RepositorioMuestras {
    void guardar(InstanciaId instancia, Muestra muestra);
    Optional<Muestra> ultima(InstanciaId instancia, Componente componente);
    List<Muestra> enRango(InstanciaId instancia, Componente c, Instant desde, Instant hasta);
}

public interface RepositorioCalibracion {
    Calibracion vigente();
    void registrar(Calibracion nueva);
}
```

`ultima(...)` no está por conveniencia: es lo que necesita el cálculo de deltas de
los contadores acumulados (`over allocation count`). El puerto lo expone porque el
dominio lo necesita, no porque la base de datos lo permita — así se diseñan los
puertos.

### Caso de uso

```java
@Service
public class MuestrearInstanciaServicio implements MuestrearInstancia {

    private final RecolectorProcesos  procesos;
    private final RecolectorMemoria   memoria;
    private final RecolectorArchivos  archivos;
    private final RepositorioMuestras muestras;
    private final RepositorioCalibracion calibraciones;
    private final MotorIndicadores    motor;      // del dominio
    private final MotorAlertas        alertas;    // del dominio

    @Override
    @Transactional
    public Isbd ejecutar(InstanciaId id) {
        Calibracion cal = calibraciones.vigente();

        Muestra mp = recolectarSeguro(() -> procesos.recolectar(id), Componente.PROCESOS, id);
        Muestra mm = recolectarSeguro(() -> memoria.recolectar(id),  Componente.MEMORIA,  id);
        Muestra ma = recolectarSeguro(() -> archivos.recolectar(id), Componente.ARCHIVOS, id);

        muestras.guardar(id, mp);
        muestras.guardar(id, mm);
        muestras.guardar(id, ma);

        Isbd isbd = motor.calcular(mp, mm, ma, cal);
        alertas.evaluar(id, List.of(mp, mm, ma), cal);
        return isbd;
    }
}
```

Fíjate en el reparto: el servicio **orquesta** (pide, guarda, delega) pero no
**decide**. Toda la lógica de salud está en `MotorIndicadores` y `MotorAlertas`, que
son clases de dominio en Java puro, sin anotaciones, testeables sin levantar nada.
Si te descubres escribiendo un `if (valor > umbral)` dentro del servicio, esa línea
pertenece al dominio.

`recolectarSeguro` implementa la regla de que un fallo de recolección es un dato: si
un recolector falla, se registra una muestra marcada como fallida y el sistema
continúa con los otros dos componentes en vez de perder el ciclo entero.

## monitor-infraestructura: adaptadores

```java
@Component
public class JdbcRecolectorProcesos implements RecolectorProcesos {

    private static final String SQL = """
        SELECT SYSTIMESTAMP AS muestreado_en, ...
        """;   // ver oracle-vistas-dinamicas

    private final JdbcClient jdbc;

    public JdbcRecolectorProcesos(@Qualifier("oracleMonitoreado") DataSource ds,
                                  MonitorProperties props) {
        this.jdbc = JdbcClient.create(ds);
    }

    @Override
    public Muestra recolectar(InstanciaId instancia) {
        try {
            return jdbc.sql(SQL)
                       .param("umbral_riesgo", props.umbralRiesgoTablespace())
                       .query(this::mapear)
                       .single();
        } catch (DataAccessException e) {
            throw new RecoleccionFallidaException(Componente.PROCESOS, instancia, e);
        }
    }
}
```

`JdbcClient` (Spring 6.1+) es más limpio que `JdbcTemplate` para consultas de solo
lectura con parámetros con nombre, que es exactamente este caso. Nota que la
excepción de Spring se traduce a una excepción del dominio antes de cruzar la
frontera: si `DataAccessException` se propagara hacia arriba, el dominio quedaría
acoplado a Spring por la puerta de atrás.

### Dos DataSource

El monitor habla con dos bases distintas y confundirlas es un error que se paga
caro (escribir el histórico dentro de la instancia monitoreada contamina justo lo
que mides):

```java
@Configuration
public class DataSourceConfig {

    @Bean("oracleMonitoreado")
    @ConfigurationProperties("monitor.datasource.monitoreada")
    public DataSource oracleMonitoreado() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(3);        // pequeño: el monitor no debe pesar
        ds.setReadOnly(true);            // no escribe en la base observada, nunca
        ds.setPoolName("monitor-lectura");
        ds.addDataSourceProperty("v$session.program", "monitor-salud-oracle");
        return ds;
    }

    @Bean("historico")
    @Primary
    @ConfigurationProperties("monitor.datasource.historico")
    public DataSource historico() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
```

`setReadOnly(true)` en el pool de la instancia monitoreada es una salvaguarda barata
que hace imposible por accidente lo que nunca debe pasar. El `v$session.program`
etiqueta las sesiones del monitor para poder identificarlas —y opcionalmente
excluirlas— en las propias métricas.

Con dos DataSource, Flyway necesita saber cuál migrar:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: MONITOR
    # apunta al 'historico'; el monitoreado NUNCA se migra
```

## Planificador

```java
@Component
@ConditionalOnProperty(value = "monitor.planificador.habilitado", havingValue = "true",
                       matchIfMissing = true)
public class PlanificadorMuestreo {

    private final MuestrearInstancia caso;
    private final RepositorioInstancias instancias;

    @Scheduled(fixedRateString = "#{@monitorProperties.muestreo().procesos()}")
    public void muestrearProcesos() {
        instancias.activas().forEach(i -> ejecutarAislado(i, Componente.PROCESOS));
    }

    /** Un fallo en una instancia no debe impedir el muestreo de las demás. */
    private void ejecutarAislado(Instancia i, Componente c) {
        try {
            caso.ejecutarComponente(i.id(), c);
        } catch (Exception e) {
            log.warn("Fallo muestreando {} de {}", c, i.alias(), e);
        }
    }
}
```

Tres detalles que evitan problemas reales:

**`@ConditionalOnProperty`.** Permite apagar el planificador en los tests. Sin esto,
cada test de integración arranca un muestreo de fondo que ensucia los datos y
produce fallos intermitentes imposibles de reproducir.

**Aislamiento por instancia.** Una excepción no capturada dentro de un `@Scheduled`
con `fixedRate` puede detener las ejecuciones futuras de ese método. El monitor
dejaría de muestrear en silencio.

**Frecuencias distintas por componente.** Archivos cada 10 minutos, procesos cada
30 segundos. Ejecutar `DBA_TABLESPACE_USAGE_METRICS` cada 30 segundos es gastar
mucho para medir algo que no cambia.

Si el proyecto crece a varias instancias, `@Scheduled` con un `ThreadPoolTaskScheduler`
de tamaño fijo evita que un muestreo lento bloquee los demás. Con una sola
instancia, el planificador por defecto (un solo hilo) es suficiente y más simple.

## Propiedades de configuración validadas

```java
@ConfigurationProperties("monitor")
@Validated
public record MonitorProperties(
        @NotNull Muestreo muestreo,
        @NotNull Pesos pesos,
        @NotNull Agregacion agregacion) {

    public record Muestreo(
            @NotNull Duration procesos,
            @NotNull Duration memoria,
            @NotNull Duration archivos) {}

    public record Pesos(
            @DecimalMin("0.0") @DecimalMax("1.0") double procesos,
            @DecimalMin("0.0") @DecimalMax("1.0") double memoria,
            @DecimalMin("0.0") @DecimalMax("1.0") double archivos) {

        @AssertTrue(message = "Los pesos de procesos, memoria y archivos deben sumar 1.0")
        public boolean sumanUno() {
            return Math.abs(procesos + memoria + archivos - 1.0) < 0.001;
        }
    }
}
```

`@AssertTrue` sobre la suma de pesos convierte un error de configuración en un
fallo de arranque con mensaje claro. Sin él, unos pesos que suman 0.95 producen un
ISBD sistemáticamente 5 % bajo, nadie lo nota, y el error aparece cuando alguien
revisa los números a mano en la semana de entrega.

`Duration` en lugar de `long milisegundos` elimina toda una clase de errores de
unidad: `PT30S` no se puede malinterpretar, `30000` sí.
