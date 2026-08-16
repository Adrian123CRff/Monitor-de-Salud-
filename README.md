# Monitor de Salud de una Base de Datos Oracle

Proyecto de EIF402: calcula un Índice de Salud de Base de Datos (ISBD) a
partir de tres sub-índices (procesos, memoria, archivos) recolectados
periódicamente de una instancia Oracle real, con alertas, histórico y un
dashboard. Arquitectura hexagonal (`monitor-dominio` en Java puro, sin
dependencias de framework) — ver `docs/adr/` para las decisiones de diseño
y `docs/plan-trabajo-pendiente.md` para lo que falta.

## Requisitos

- **Java 21** o superior (el proyecto trae su propio Maven Wrapper, no hace
  falta instalar Maven aparte).
- **Docker** y **Docker Compose** (para Oracle Free y el histórico Postgres).
- **Node.js** solo si querés correr el frontend en modo desarrollo con hot
  reload (`npm run dev`) — para compilar el proyecto completo con
  `mvnw install`, Node lo descarga solo el build (ver ADR 0004).

## Arrancar todo en Docker (la forma más simple)

```bash
docker compose up -d --build
```

Levanta Oracle Free (`monitor-oracle`, puerto 1521), Postgres (`monitor-historico`,
puerto 5432) y `monitor-api` (puerto 8080, con el dashboard React ya
compilado adentro — ver ADR 0004). Los healthchecks hacen que `monitor-api`
espere a que Oracle y Postgres estén realmente listos antes de arrancar.

Con todo arriba:

- Dashboard: <http://localhost:8080>
- API: <http://localhost:8080/api/v1/...>

El planificador (`monitor.planificador.habilitado: true`) empieza a
muestrear solo, cada 60s (`monitor.muestreo.intervalo`).

## Desarrollo local (backend en el host, solo Oracle/Postgres en Docker)

Este es el flujo que se usó para construir el proyecto: más rápido para
iterar porque no hay que reconstruir la imagen en cada cambio.

```bash
docker compose up -d oracle-monitoreado historico
./mvnw -pl monitor-api spring-boot:run   # o .\mvnw.cmd en Windows
```

`monitor-api` corriendo en el host usa `localhost` para conectarse a los
contenedores (puertos publicados) — es el valor por defecto en
`application.yml`, no hace falta ninguna variable de entorno.

### Frontend con hot reload

```bash
cd monitor-web
npm install
npm run dev
```

Sirve en <http://localhost:5173> con un proxy a `/api` hacia
`http://localhost:8080` (ver `vite.config.ts`) — necesita `monitor-api`
corriendo aparte (paso anterior).

## Tests

```bash
./mvnw test
```

Corre los tests unitarios de los cuatro módulos backend (rápidos, sin
Oracle ni Postgres reales) y compila el frontend como parte del build de
`monitor-api` (ver ADR 0004 / `frontend-maven-plugin`).

Las clases con sufijo `*IT` (integración) están deliberadamente excluidas
de esto — necesitan Oracle y Postgres reales corriendo. Con
`docker compose up -d` levantado:

```bash
./mvnw -pl monitor-infraestructura test -Dtest=JdbcRepositorioIndicesIT
# o "-Dtest=ClaseA,ClaseB" para correr varias
```

### Frontend

```bash
cd monitor-web
npm run test
```

Vitest + React Testing Library — no forma parte de `npm run build` (que
`mvn test`/`mvn install` sí dispara vía `frontend-maven-plugin`), así que un
test roto nunca bloquea el build del jar.

## Estructura

```
monitor-dominio/          Java puro: indicadores, alertas, calibración
monitor-aplicacion/       casos de uso + puertos (interfaces)
monitor-infraestructura/  adaptadores JDBC/Oracle/Postgres + Flyway
monitor-api/              REST + arranque Spring Boot + sirve el frontend
monitor-web/              dashboard React + TypeScript + Vite
docs/adr/                 decisiones de arquitectura (por qué, no solo qué)
docs/diagramas/           C4
docs/plan-trabajo-pendiente.md   backlog dividido por módulos
```
