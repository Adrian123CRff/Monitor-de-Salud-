# Pruebas, CI y observabilidad

## La pirámide, aplicada a este proyecto

La arquitectura hexagonal paga su costo justo aquí: como el dominio no depende de
nada, la mayoría de los tests son instantáneos.

```
        ╱╲          e2e (2-3)         flujo completo con Testcontainers
       ╱  ╲
      ╱────╲        integración (10-15)  adaptadores contra Oracle real
     ╱      ╲
    ╱────────╲      arquitectura (5-8)   ArchUnit
   ╱          ╲
  ╱────────────╲    unitarias (50+)      dominio puro, sin framework
```

**Los tests unitarios del dominio son los que más valor dan y los más baratos.**
Normalización, agregación, veto, histéresis y apertura/cierre de alertas se prueban
con números inventados en milisegundos. No necesitan Spring ni base de datos.

## Tests del dominio

```java
class MotorIndicadoresTest {

    @Test
    void el_veto_marca_critico_aunque_la_puntuacion_sea_alta() {
        // El caso de la sección 20 de la propuesta: dos componentes hundidos,
        // uno excelente. La media podría disimularlo; el veto no debe permitirlo.
        var ip = new Indicador(PROCESOS, 25, Map.of());
        var im = new Indicador(MEMORIA,  30, Map.of());
        var ia = new Indicador(ARCHIVOS, 98, Map.of());

        Isbd r = motor.combinar(ip, im, ia, calibracionConVeto(40));

        assertThat(r.estado()).isEqualTo(Estado.CRITICO);
        assertThat(r.estadoPorVeto()).isTrue();
        assertThat(r.causas()).hasSize(2)
            .anyMatch(c -> c.contains("PROCESOS"))
            .anyMatch(c -> c.contains("MEMORIA"));
    }

    @Test
    void la_media_geometrica_castiga_mas_que_la_aritmetica() {
        var ip = new Indicador(PROCESOS, 25, Map.of());
        var im = new Indicador(MEMORIA,  30, Map.of());
        var ia = new Indicador(ARCHIVOS, 98, Map.of());

        double aritmetica = motor.combinar(ip, im, ia, cal(ARITMETICA)).puntuacion();
        double geometrica = motor.combinar(ip, im, ia, cal(GEOMETRICA)).puntuacion();

        assertThat(aritmetica).isCloseTo(52.3, within(0.5));
        assertThat(geometrica).isCloseTo(43.0, within(0.5));
        assertThat(geometrica).isLessThan(aritmetica);
    }

    @Test
    void un_componente_en_cero_hunde_la_media_geometrica() {
        // Propiedad clave: con geométrica, si algo está totalmente roto,
        // el índice global no puede ser alto. La aritmética sí lo permitiría.
        var r = motor.combinar(ind(PROCESOS, 0), ind(MEMORIA, 100),
                               ind(ARCHIVOS, 100), cal(GEOMETRICA));
        assertThat(r.puntuacion()).isLessThan(1.0);
    }
}

class NormalizadorTest {

    @Test
    void invierte_la_polaridad_de_una_utilizacion() {
        // 95 % de utilización de procesos debe dar una puntuación BAJA.
        // Es el bug que la propuesta original arrastra: verificarlo explícitamente.
        var umbral = umbral("util_procesos_pct", /*ok*/70, /*critico*/95, /*invertir*/true);
        assertThat(normalizador.puntuar(95.0, umbral)).isLessThan(10.0);
        assertThat(normalizador.puntuar(30.0, umbral)).isEqualTo(100.0);
    }

    @Test
    void una_metrica_de_mas_es_mejor_no_se_invierte() {
        var umbral = umbral("cache_hit_pct", /*ok*/90, /*critico*/50, /*invertir*/false);
        assertThat(normalizador.puntuar(95.0, umbral)).isEqualTo(100.0);
        assertThat(normalizador.puntuar(40.0, umbral)).isEqualTo(0.0);
    }
}
```

Estos tests son, además, **la especificación ejecutable** de las decisiones de
diseño. El nombre del método dice la regla, y si alguien la rompe el build falla.
Vale la pena nombrarlos en español y en forma de frase por esa razón: se leen como
un documento de requisitos que no puede quedar desactualizado.

## Tests de integración con Testcontainers

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class JdbcRecolectorProcesosIT {

    @Container
    static final OracleContainer ORACLE =
        new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withUsername("monitor")
            .withPassword("monitor")
            .withReuse(true);          // clave: reutiliza el contenedor entre ejecuciones

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("monitor.datasource.monitoreada.url",      ORACLE::getJdbcUrl);
        r.add("monitor.datasource.monitoreada.username", ORACLE::getUsername);
        r.add("monitor.datasource.monitoreada.password", ORACLE::getPassword);
    }

    @Autowired JdbcRecolectorProcesos recolector;

    @Test
    void lee_las_variables_de_procesos_de_una_instancia_real() {
        Muestra m = recolector.recolectar(new InstanciaId(1));

        assertThat(m.componente()).isEqualTo(Componente.PROCESOS);
        assertThat(m.valor("p1")).isGreaterThan(0);   // siempre hay procesos
        assertThat(m.valor("p3")).isGreaterThan(0);   // y sesiones
        assertThat(m.valores()).containsKeys("p1","p2","p3","p4","p5","p6","p7","p8");
    }

    @Test
    void detecta_una_sesion_bloqueada_provocada_a_proposito() throws Exception {
        try (var bloqueante = abrirSesion(); var bloqueada = abrirSesion()) {
            bloqueante.ejecutar("UPDATE prueba SET v = 1 WHERE id = 1");  // sin commit
            var futuro = ejecutarAsync(bloqueada, "UPDATE prueba SET v = 2 WHERE id = 1");
            esperarHasta(() -> recolector.recolectar(ID).valor("p6") >= 1);

            assertThat(recolector.recolectar(ID).valor("p6")).isGreaterThanOrEqualTo(1);
            bloqueante.rollback();
            futuro.get(5, SECONDS);
        }
    }
}
```

El segundo test es el que de verdad demuestra algo. Provocar un bloqueo real y
verificar que el monitor lo detecta es la prueba de que el sistema funciona; leer
que hay procesos solo prueba que la conexión existe. Este tipo de test —**provocar
la condición y verificar la detección**— es lo que hay que replicar para tablespaces
llenos y presión de PGA, y lo que da material sólido para el capítulo de pruebas del
informe.

Dos avisos prácticos: `withReuse(true)` requiere `testcontainers.reuse.enable=true`
en `~/.testcontainers.properties` y ahorra minutos en cada ejecución, porque
levantar Oracle no es instantáneo ni con las imágenes `faststart`. Y marca estos
tests con un perfil o sufijo (`*IT`) para poder ejecutar solo los unitarios durante
el desarrollo: si cada compilación levanta Oracle, dejarás de ejecutar los tests.

## Tests de arquitectura

Los de ArchUnit del SKILL.md principal, más dos que atrapan errores frecuentes:

```java
@ArchTest
static final ArchRule los_adaptadores_implementan_puertos =
    classes().that().resideInAPackage("..infraestructura.oracle..")
        .and().haveSimpleNameStartingWith("Jdbc")
        .should().implement(
            JavaClass.Predicates.resideInAPackage("..aplicacion.puerto.salida.."));

@ArchTest
static final ArchRule ninguna_clase_usa_System_out =
    noClasses().should().accessClassesThat()
        .belongToAnyOf(System.class)
        .because("usa el logger; System.out no se puede filtrar ni enrutar");
```

## Integración continua

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  construir:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Tests unitarios y de arquitectura
        run: mvn -B verify -DskipITs

      - name: Tests de integración
        run: mvn -B verify -Dtest.skip.unit=true

      - name: Frontend
        working-directory: monitor-web
        run: |
          npm ci
          npm run lint
          npm run build
```

Separar los unitarios de los de integración en dos pasos hace que un fallo de
lógica se reporte en 40 segundos en lugar de esperar cinco minutos a que Oracle
arranque. El ciclo de retroalimentación corto es lo que determina si de verdad
usarás los tests.

Si el proyecto se entrega con repositorio, un badge de CI verde en el README es
evidencia inmediata y verificable de que el proyecto compila y pasa sus pruebas.

## Observabilidad: el monitor también se monitorea

Hay algo elegante —y muy defendible en la presentación— en que un sistema de
monitoreo esté él mismo instrumentado:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints.web.exposure.include: health,metrics,info,prometheus
  endpoint.health.show-details: when-authorized
  health.db.enabled: true
```

Métricas propias que vale la pena registrar con Micrometer:

```java
@Component
public class MetricasMonitor {
    private final Timer tiempoRecoleccion;
    private final Counter recoleccionesFallidas;
    private final AtomicInteger ultimoIsbd = new AtomicInteger();

    public MetricasMonitor(MeterRegistry registro) {
        this.tiempoRecoleccion = Timer.builder("monitor.recoleccion.duracion")
            .description("Tiempo de recolección por componente")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registro);
        this.recoleccionesFallidas = registro.counter("monitor.recoleccion.fallos");
        registro.gauge("monitor.isbd.actual", ultimoIsbd);
    }
}
```

`monitor.recoleccion.duracion` responde con datos a la pregunta "¿cuánto le cuesta
a la instancia ser monitoreada?" — que es exactamente la objeción que te van a
plantear en la defensa. Poder responder "el percentil 95 de la recolección es de
40 ms cada 30 segundos, es decir menos del 0.15 % del tiempo" es mucho más
convincente que "es liviano".

Un `HealthIndicator` propio cierra el círculo:

```java
@Component
public class SaludConexionOracle implements HealthIndicator {
    @Override
    public Health health() {
        return ultimaRecoleccionExitosa()
            ? Health.up().withDetail("ultimaMuestra", ultimoMomento).build()
            : Health.down().withDetail("motivo", ultimoError).build();
    }
}
```

## Qué medir de la calidad del código

Sin obsesionarse: en un proyecto de curso el objetivo es tener criterio, no
perseguir métricas.

- **Cobertura del dominio**: apunta alto (>80 %). Es barato y es donde está la
  lógica que importa. La cobertura global es una métrica mucho menos informativa.
- **JaCoCo** con umbral mínimo en el módulo de dominio, para que el build falle si
  baja.
- **Análisis estático**: SpotBugs o el `-Xlint:all` del compilador. SonarQube si te
  sobra tiempo, pero no es imprescindible.

Si tienes el plugin `engineering` instalado, `/engineering:code-review` y
`/engineering:testing-strategy` cubren esta parte con más detalle.
