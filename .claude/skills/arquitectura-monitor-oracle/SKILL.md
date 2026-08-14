---
name: arquitectura-monitor-oracle
description: Cómo estructurar el proyecto Monitor de Salud de Oracle como un sistema bien diseñado — arquitectura hexagonal (puertos y adaptadores) en un multi-módulo Maven con Spring Boot, frontend React separado, reglas de dependencia verificadas automáticamente con ArchUnit, migraciones con Flyway, tests con Testcontainers, diagramas C4 y registros de decisión (ADR). Usa esta skill SIEMPRE que haya que crear la estructura del proyecto, decidir en qué módulo o paquete va una clase, diseñar la API REST, configurar el pom.xml, escribir el planificador de muestreo, montar el frontend, definir la estrategia de pruebas o el pipeline de CI. Aplica también cuando la pregunta sea de diseño en general ("dónde pongo esto", "cómo separo las capas", "esto está bien arquitecturado", "cómo empiezo el proyecto") o cuando el usuario quiera aprender a estructurar proyectos como arquitecto de software, no solo hacer que compile.
---

# Arquitectura del Monitor de Salud de Oracle

Guía de estructura para construir el monitor como un sistema que se pueda defender,
extender y mantener. El objetivo no es que funcione —eso es el piso— sino que
alguien que no lo escribió pueda entenderlo, y que tú puedas justificar cada
decisión.

## La idea central: proteger el dominio

El valor intelectual de este proyecto no está en conectarse a Oracle ni en dibujar
gráficos. Está en las **reglas de salud**: cómo se normaliza una métrica, cómo se
combinan los indicadores, cuándo un componente veta el estado global, cuándo una
alerta se abre y cuándo se cierra.

Esas reglas son lo que hay que proteger, porque son lo que cambia más veces durante
el proyecto y lo que más caro cuesta reescribir. Si viven mezcladas con JDBC y con
anotaciones de Spring, cada recalibración obliga a tocar código de infraestructura,
cada test necesita una base de datos y refactorizar da miedo. Si viven aisladas en
Java puro, se prueban en milisegundos, se leen sin ruido y se pueden reescribir sin
tocar nada más.

De ahí la arquitectura hexagonal. No es ceremonia académica: es la respuesta directa
a "¿qué parte de esto va a cambiar más veces?".

```
                 ┌─────────────────────────────────┐
   Oracle ──────►│  adaptador   ┌───────────────┐  │
   (fuente)      │   entrada    │               │  │
                 │              │    DOMINIO    │  │
                 │              │  (Java puro)  │  │
   React ◄───────│  adaptador   │               │  │
   (salida)      │   salida     └───────────────┘  │
                 │                                 │
   Postgres ◄────│  adaptador de persistencia      │
   (histórico)   └─────────────────────────────────┘
```

El dominio no sabe que existe Oracle, ni Spring, ni REST, ni React. Sabe de
indicadores, umbrales y alertas.

## Estructura del proyecto

Multi-módulo Maven. Cuatro módulos backend con dependencias en un solo sentido, más
el frontend:

```
monitor-salud-oracle/
├── pom.xml                     ← parent, <packaging>pom</packaging>
├── docs/
│   ├── adr/                    ← registros de decisión de arquitectura
│   │   ├── 0001-arquitectura-hexagonal.md
│   │   ├── 0002-media-geometrica-para-isbd.md
│   │   └── 0003-persistir-valores-crudos.md
│   └── diagramas/              ← C4: contexto, contenedores, componentes
│
├── monitor-dominio/            ← Java puro. CERO dependencias de framework.
│   └── src/main/java/cr/ac/una/monitor/dominio/
│       ├── modelo/             Muestra, Variable, Indicador, Isbd, Alerta, Estado
│       ├── normalizacion/      Normalizador y sus estrategias
│       ├── agregacion/         cálculo de IP/IM/IA/ISBD, reglas de veto
│       ├── alertas/            apertura, cierre, histéresis, deduplicación
│       └── calibracion/        Umbral, Peso, Calibracion
│
├── monitor-aplicacion/         ← casos de uso + PUERTOS (interfaces)
│   └── src/main/java/cr/ac/una/monitor/aplicacion/
│       ├── puerto/entrada/     MuestrearInstancia, ConsultarSalud, ConsultarHistorico
│       ├── puerto/salida/      RecolectorProcesos, RecolectorMemoria,
│       │                       RecolectorArchivos, RepositorioMuestras,
│       │                       RepositorioAlertas, RepositorioCalibracion
│       └── servicio/           implementaciones de los casos de uso
│
├── monitor-infraestructura/    ← ADAPTADORES (implementan los puertos de salida)
│   └── src/main/java/cr/ac/una/monitor/infraestructura/
│       ├── oracle/             JdbcRecolectorProcesos, ...Memoria, ...Archivos
│       ├── persistencia/       repositorios JPA/JDBC + entidades + Flyway
│       └── planificador/       @Scheduled que dispara el muestreo
│
├── monitor-api/                ← adaptador de entrada + arranque
│   └── src/main/java/cr/ac/una/monitor/api/
│       ├── MonitorApplication.java
│       ├── rest/               controladores REST
│       ├── dto/                DTOs de respuesta (nunca entidades del dominio)
│       └── config/             @ConfigurationProperties, beans, CORS
│
└── monitor-web/                ← React + Vite (build independiente)
    ├── package.json
    └── src/
```

### La regla de dependencia

```
monitor-api ──────────┐
                      ├──► monitor-aplicacion ──► monitor-dominio
monitor-infraestructura┘
```

- `monitor-dominio` **no depende de nada**. Ni Spring, ni Jackson, ni JDBC.
- `monitor-aplicacion` depende solo de `monitor-dominio`.
- `monitor-infraestructura` y `monitor-api` dependen de aplicación y dominio.
- **Nada depende de `monitor-api`.**

Las flechas apuntan hacia adentro, siempre. Que el dominio sea un módulo Maven
separado sin dependencias en su `pom.xml` hace la regla **físicamente imposible de
violar**: si alguien escribe `import org.springframework...` en el dominio, no
compila. Eso vale más que cualquier convención documentada, porque las convenciones
se erosionan y el compilador no.

El detalle de cada módulo, con las interfaces y clases clave, está en
`references/estructura-backend.md`.

## Umbrales y pesos son configuración, no código

La segunda decisión que más impacto tiene. Vas a recalibrar muchas veces; si los
números están escritos en el código, cada recalibración es un ciclo de
editar-compilar-desplegar y el histórico queda sin explicación.

Los umbrales y pesos viven en la tabla `MONITOR_UMBRAL` (ver `monitor-salud-oracle`),
se cargan al arrancar y se pueden recargar en caliente. `application.yml` solo
guarda los valores por defecto para arrancar en limpio:

```yaml
monitor:
  muestreo:
    procesos:  PT30S      # ISO-8601 Duration: legible y sin ambigüedad de unidad
    memoria:   PT60S
    archivos:  PT10M
  pesos:
    procesos: 0.30
    memoria:  0.35
    archivos: 0.35
  agregacion:
    metodo: GEOMETRICA    # GEOMETRICA | ARITMETICA
    veto:
      habilitado: true
      umbral-componente: 40
```

Mapéalo con `@ConfigurationProperties` y **valídalo al arrancar** con
`@Validated`: si los pesos no suman 1, el arranque debe fallar con un mensaje claro.
Un sistema que arranca con una configuración incoherente y produce números
silenciosamente incorrectos es peor que uno que no arranca.

## API REST

```
GET  /api/v1/instancias                          lista de instancias monitoreadas
GET  /api/v1/instancias/{id}/salud               ISBD actual + IP/IM/IA + estado
GET  /api/v1/instancias/{id}/salud/historico     serie temporal (desde, hasta, granularidad)
GET  /api/v1/instancias/{id}/componentes/{c}     detalle de variables de un componente
GET  /api/v1/instancias/{id}/tablespaces         detalle por tablespace
GET  /api/v1/instancias/{id}/alertas             alertas (abiertas | todas, por rango)
POST /api/v1/instancias/{id}/muestrear           forzar muestreo (para demo y pruebas)
GET  /api/v1/calibracion                         umbrales y pesos vigentes
PUT  /api/v1/calibracion                         nueva calibración
```

Tres criterios detrás de estos endpoints:

**Versionado desde el primer día.** `/api/v1/` cuesta cinco caracteres ahora y evita
romper el frontend después. Es gratis al principio y caro de añadir luego.

**Los DTO no son las clases del dominio.** Serializar directamente objetos del
dominio acopla el contrato HTTP a la estructura interna: cualquier refactor del
dominio se convierte en un cambio incompatible de API. Mapea explícitamente.

**Errores con formato uniforme.** Usa `ProblemDetail` (RFC 9457), que Spring soporta
de forma nativa. Que todos los errores tengan la misma forma le simplifica la vida
al frontend y es un detalle que se nota.

`POST /muestrear` merece una nota: parece un atajo de conveniencia y en realidad es
la clave de una demostración en vivo convincente. Poder decir "voy a provocar
presión de PGA... y ahora fuerzo un muestreo" y que el dashboard cambie delante del
tribunal vale mucho más que una captura de pantalla.

## Verificar la arquitectura con tests

Aquí está la diferencia entre decir que tienes una arquitectura y demostrarlo. Los
tests de ArchUnit hacen que una violación de las reglas **rompa la compilación**:

```java
@AnalyzeClasses(packages = "cr.ac.una.monitor")
class ArquitecturaTest {

    @ArchTest
    static final ArchRule el_dominio_no_conoce_frameworks =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                                "com.fasterxml.jackson..", "oracle.jdbc..");

    @ArchTest
    static final ArchRule las_capas_respetan_su_orden =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Dominio").definedBy("..dominio..")
            .layer("Aplicacion").definedBy("..aplicacion..")
            .layer("Infraestructura").definedBy("..infraestructura..")
            .layer("Api").definedBy("..api..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infraestructura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacion").mayOnlyBeAccessedByLayers("Api", "Infraestructura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers(
                "Aplicacion", "Infraestructura", "Api");

    @ArchTest
    static final ArchRule los_controladores_no_tocan_repositorios =
        noClasses().that().resideInAPackage("..api.rest..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura.persistencia..");
}
```

Incluye una captura de estos tests pasando en el informe. Es evidencia objetiva de
que la arquitectura descrita en el documento es la que está implementada — y la
brecha entre esas dos cosas es precisamente lo que un evaluador busca.

## Pila tecnológica

Versiones vigentes a agosto de 2026. Verifícalas al arrancar: cambian.

| Componente | Elección | Nota |
|---|---|---|
| Java | 21 LTS (o 25 LTS) | 21 es el LTS más extendido; 25 si quieres lo más reciente |
| Spring Boot | 4.1.x | GA desde junio 2026; soporta Java 17–26 |
| Build | Maven multi-módulo | Gradle es válido; Maven documenta mejor los límites entre módulos |
| Driver Oracle | `com.oracle.database.jdbc:ojdbc17` 23.26.x | Usa `ojdbc-bom` para alinear los artefactos relacionados |
| Migraciones | Flyway | El esquema `MONITOR_*` versionado desde la primera línea |
| Histórico | Oracle o PostgreSQL | Separado de la instancia monitoreada, sí o sí |
| Tests | JUnit 5, AssertJ, ArchUnit, Testcontainers | `testcontainers:oracle-free` para integración |
| Frontend | React 19 + Vite + TypeScript | Build independiente |
| Gráficos | Recharts o Apache ECharts | ECharts va mejor con series temporales largas |
| Observabilidad | Actuator + Micrometer | El monitor también debe poder monitorearse |

Detalles de configuración y `pom.xml` en `references/estructura-backend.md`;
frontend en `references/frontend-react.md`; tests y CI en
`references/calidad-tests-ci.md`.

## Documentar decisiones: ADR

Cada decisión de arquitectura no obvia se escribe en `docs/adr/` como un archivo
corto en formato MADR: **contexto → decisión → consecuencias**. Diez o quince
líneas, no un ensayo.

Sirven para dos cosas concretas. Primero, dentro del proyecto: dentro de dos meses
no recordarás por qué elegiste media geométrica, y la alternativa a un ADR es
volver a discutirlo desde cero. Segundo, en la evaluación: un directorio de ADRs
demuestra que hubo **razonamiento**, no solo tecleo. Es de las cosas que más
distinguen un trabajo de nivel profesional.

Candidatos naturales en este proyecto: arquitectura hexagonal; media geométrica vs.
aritmética; persistir crudos además de scores; separar el esquema del monitor de la
instancia monitoreada; muestreo barato con drill-down bajo demanda; umbrales en base
de datos en lugar de en código.

Si tienes instalado el plugin `engineering`, su comando `/engineering:architecture`
genera ADRs con el formato completo. Úsalo.

## Diagramas C4

Cuatro niveles, de los que en este proyecto valen la pena los tres primeros:

1. **Contexto** — el monitor, el DBA que lo usa, la instancia Oracle que observa.
2. **Contenedores** — backend Spring, frontend React, base histórica, instancia
   monitoreada.
3. **Componentes** — dentro del backend: recolectores, motor de indicadores, motor
   de alertas, API.

Escríbelos en PlantUML o Mermaid dentro del repositorio, no como imágenes sueltas:
un diagrama en texto se versiona junto al código y se actualiza cuando el código
cambia. Los diagramas ASCII de la propuesta original son un buen punto de partida —
ya tienen la estructura correcta, solo hay que formalizarlos.

## Orden de construcción sugerido

El orden importa: construir de adentro hacia afuera permite tener algo demostrable
pronto y evita el escenario clásico de tres semanas de infraestructura sin nada que
enseñar.

1. **Dominio primero, sin base de datos.** Modelo, normalizadores, agregación,
   veto. Con tests unitarios y datos inventados. En dos días tienes el corazón del
   sistema funcionando y probado. Es la parte con más valor intelectual y la que da
   más contenido al informe.
2. **Puertos y un adaptador falso.** Un `RecolectorProcesos` que devuelve datos
   simulados. Ya puedes ejecutar el flujo completo de punta a punta.
3. **Adaptador Oracle real.** Con Testcontainers desde el principio.
4. **Persistencia + Flyway.** El esquema `MONITOR_*`.
5. **API REST.** Sobre casos de uso que ya funcionan.
6. **Planificador.** Muestreo automático.
7. **Frontend.** Contra una API estable.
8. **Calibración.** Con datos reales acumulados.

Los pasos 1 y 2 no necesitan Oracle. Puedes tener el núcleo del proyecto terminado
y probado antes de resolver un solo problema de conexión — y si el entorno Oracle
se complica (suele complicarse), no te bloquea.

## Skills relacionadas

- `monitor-salud-oracle` — el dominio: qué se mide y qué significa.
- `oracle-vistas-dinamicas` — el SQL que va dentro de los adaptadores.
- `diseno-de-indicadores` — la lógica que va dentro del dominio.

## Archivos de referencia

- `references/estructura-backend.md` — poms, interfaces clave, planificador, config.
- `references/frontend-react.md` — estructura React, cliente de API, dashboard.
- `references/calidad-tests-ci.md` — pirámide de pruebas, Testcontainers, CI, Actuator.
