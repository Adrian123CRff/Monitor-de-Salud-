# Plan de trabajo pendiente

Backlog de lo que quedó fuera después de construir procesos/memoria/archivos,
el planificador, la API REST, el dominio de alertas y el dashboard. Dividido
en módulos independientes entre sí (se pueden trabajar en cualquier orden,
salvo donde se indica una dependencia explícita).

Convención: cada tarea indica si es **código** (se hace en una sesión de
trabajo) o **operación** (requiere que el monitor corra solo un tiempo real,
no se puede acelerar picoteando código).

---

## Módulo A — Infraestructura de despliegue
*Bloquea la demo pública (ADR 0004/0005). Sin esto, todo lo demás vive solo
en Docker local.*

- **A1. Dockerfile para monitor-api.** Multi-stage: una etapa con Maven +
  Node (corre `mvn install`, que ya dispara `frontend-maven-plugin` y deja
  el jar con el frontend adentro — ver commit `c21f3c8`), otra etapa solo
  con JRE que copia el jar final. Sin esto no hay imagen que subir a ningún
  lado.
- **A2. docker-compose de demo.** Ajustar el `docker-compose.yml` actual
  (hoy solo tiene Oracle + Postgres, pensado para desarrollo) para incluir
  `monitor-api` como tercer servicio, con las variables de entorno de
  `application.yml` (`MONITOR_DB_PASSWORD`, etc.) inyectadas por entorno,
  no hardcodeadas.
- **A3. Pipeline CI (GitHub Actions).** `mvn test` (sin ITs, no hay Oracle
  en el runner por defecto) en cada push/PR — hoy cero verificación
  automática, todo lo validamos a mano esta sesión. Job separado opcional
  con Testcontainers para las ITs si el runner lo soporta.
- **A4. README real.** Hoy solo dice "instalá Java y corré `mvnw test`".
  Falta: cómo levantar Docker (`docker compose up -d`), cómo arrancar
  `monitor-api`, dónde ver el dashboard, variables de entorno necesarias.

## Módulo B — Calibración con datos reales
*El proceso completo de `references/calibracion.md` (skill diseno-de-indicadores)
nunca se hizo — los umbrales siguen siendo "valores de diseño, no calibrados".
Es la parte que más pesa en la evaluación del profesor y la única que no se
puede resolver solo escribiendo código.*

- [x] **B0. Umbrales como datos, no como código.** Prerrequisito que estaba
  implícito y sin registrar: `MuestrearInstanciaServicio` leía
  `UmbralesIniciales` (compilado) en cada ciclo, así que "ajustar umbrales"
  (B4) significaba editar Java y redesplegar en cada iteración de
  calibración. Ahora salen de `monitor_umbral_puntuacion` (V8) vía
  `RepositorioUmbrales` — calibrar es un `UPDATE`. Incluye los perfiles por
  tamaño (`PEQUENA`/`MEDIANA`/`GRANDE`, herencia por variable desde
  `ESTANDAR`) que pide el requisito "paramétrico" de las notas de clase.
  Ver ADR 0007.

  La tabla `monitor_umbral` de V1 se eliminó: mezclaba los umbrales de
  puntuación con los de alerta y ataba ambos a `calibracion_id`, lo que los
  dejaba huérfanos cada vez que `registrar()` cambiaba los pesos.

  **Pendiente del mismo tipo**: los umbrales de *alerta* (`AlertasIniciales`
  / `UmbralAlerta`, con histéresis y confirmación) siguen en código y
  necesitan su propia tabla. B4 no está completo sin eso.

- **B1. Línea base — operación, 1-2 semanas.** Dejar el planificador
  corriendo en condiciones normales, guardando crudos, sin tocar umbrales.
  Debe cubrir días laborales y fines de semana. **Esto no se puede acelerar
  en una sesión de código: es tiempo real corriendo.**
- **B2. Percentiles — código, una vez que B1 tenga datos.** Consulta SQL de
  percentiles (p50/p90/p95/p99) por variable contra el histórico en
  Postgres, matching el patrón de `references/calibracion.md`.
- **B3. Pruebas de estrés — código + operación corta.** Provocar a
  propósito: llenar un tablespace pequeño, abrir muchas sesiones, forzar
  presión de PGA con `sort_area_size` chico. Anotar dónde caen los valores
  reales (ya lo hicimos una vez para procesos/memoria en esta sesión, falta
  repetirlo sistemáticamente y con los umbrales de alerta, no solo de
  puntuación).
- **B4. Aplicar los valores calibrados.** Con B2+B3, reemplazar los valores
  de diseño por los calibrados, documentando la fuente de cada uno
  (percentil observado vs. límite duro vs. prueba de estrés).

  Para los umbrales de **puntuación** ya no hace falta tocar código (B0):
  es un `UPDATE` sobre `monitor_umbral_puntuacion`, y la columna `fuente`
  es donde va esa justificación. Ojo: si se cambia un valor del perfil
  `ESTANDAR`, hay que cambiarlo también en `UmbralesIniciales` (que sigue
  siendo el respaldo en código) o `JdbcRepositorioUmbralesIT` falla — es
  a propósito, para que las dos copias no diverjan en silencio.

  Para los umbrales de **alerta** todavía sí hay que editar
  `AlertasIniciales` y recompilar, hasta que tengan su propia tabla.
- **B5. Justificar los pesos (AHP o análisis de sensibilidad).** Los
  30/35/35 siguen siendo los del documento original. AHP (comparación por
  pares con verificación de consistencia CR) es lo más defendible; un
  análisis de sensibilidad más simple ("¿cambia mucho el resultado con
  pesos distintos?") es la alternativa más barata.

## Módulo C — Testing que falta
*`monitor-api` solo tenía el ArchUnit test; el frontend no tenía ninguno.*

- [x] **C1. Tests de controladores REST.** `@WebMvcTest` (Spring Boot 4.1.0
  lo movió a `spring-boot-webmvc-test` / `org.springframework.boot.webmvc.test.autoconfigure`
  — hubo que agregar `spring-boot-starter-webmvc-test` como dependencia de
  test explícita) para `SaludController`, `ComponentesController`,
  `TablespacesController`, `AlertasController`, `CalibracionController`.
  16 tests, cubren el mapeo DTO/JSON y los códigos de error de
  `ManejadorErrores`.
- [x] **C2. Tests de frontend.** Vitest + React Testing Library (`npm run
  test`, no forma parte de `npm run build`): `utilidades.ts`, `api/cliente.ts`
  (fetch mockeado, 404 → `SinDatosAunError`), `IsbdHero` (veto/parcial),
  `AlertasPanel`, `TablespacesPanel` (orden peor-primero). 38 tests.
- **C3. Verificación visual real en navegador.** Pendiente desde que se
  declinó `claude-in-chrome` esta sesión — la próxima vez que esté
  disponible, correr el dev server y confirmar visualmente que el dashboard
  renderiza como se espera (hoy solo está verificado que los datos fluyen
  correctamente y que los componentes renderizan el DOM esperado bajo
  jsdom, no el render real en un navegador).

## Módulo D — Alertas: variables adicionales
*`ConfirmadorTemporal` existe y está probado (ver `EvaluadorNivelTest`,
`ConfirmadorTemporalTest`) pero no estaba conectado — ninguna de las dos
variables iniciales lo necesitaba.*

- [x] **D1. Sesiones bloqueadas (`p6_sesiones_bloqueadas`).** Confirmación
  "2 de 3" (`AlertasIniciales.sesionesBloqueadas()`).
- [x] **D2. Presión de PGA (`m8_over_alloc_delta`).** Confirmación "3 de 5"
  (`AlertasIniciales.presionPga()`), sobre la delta del intervalo, nunca el
  acumulado.

  Implementación: `UmbralAlerta.conConfirmacion()` (nuevo factory) +
  `MuestrearInstanciaServicio.confirmarSiHaceFalta()` -- solo el disparo
  inicial (NORMAL → algo distinto) exige N de las últimas M muestras
  (`RepositorioMuestras.ultimasN()`, nuevo método de puerto); escalar o
  cerrar un episodio ya abierto es inmediato, sin reconfirmar. Cobertura:
  5 tests nuevos en `MuestrearInstanciaServicioTest` (no confirma con 1/3,
  confirma con 2/3, escala sin reconfirmar, no confirma con 2/5, confirma
  con 3/5) + `JdbcRepositorioMuestrasIT.ultimasN_...` contra Postgres real.
- [x] **D3. Tercera variable: `b1_procesos_caidos`.** Binaria y grave,
  mismo patrón que `datafilesOffline()` -- `AlertasIniciales.procesosCaidos()`.
  Complementa (no reemplaza) el veto absoluto que `MotorIndicadores` ya
  aplica sobre IP_fondo: el veto describe solo el ciclo actual, no deja
  episodio con apertura/cierre en `MONITOR_ALERTAS` ni aparece en el panel
  de alertas del dashboard.

  Se descartó la otra candidata (utilización de procesos/sesiones): la
  skill la marca "EWMA α=0.3 + histéresis" -- un tercer mecanismo
  (suavizado exponencial) no implementado todavía, y que necesitaría datos
  reales de B1 para elegir un umbral defendible. Calibrarla a ciegas sería
  peor que no tenerla; se retoma cuando B1 tenga suficiente cobertura.

  Cobertura: test nuevo en `MuestrearInstanciaServicioTest` (proceso de
  fondo caído abre CRITICO en `MONITOR_ALERTAS`, además del veto del ISBD).
  Verificado en vivo contra el `monitor-api` de docker compose.

## Módulo E — Frontend: piezas pendientes
- [x] **E1. UI de calibración.** `CalibracionPanel` (toggle desde el botón
  "Calibración" del header) -- edita pesos/umbral de veto/checkbox y hace
  `PUT /calibracion`, con validación de que los pesos sumen 1.0 en el
  cliente (deshabilita "Guardar" si no, igual que la validación del
  dominio) antes de disparar la llamada.
- [x] **E2. Drill-down por componente.** Los tiles de `IndicadoresTiles`
  ahora son clicables (`onSeleccionar`); `ComponenteDetalle` consume
  `GET /componentes/{c}` y muestra cada vista (usuarios/fondo/actual) con
  sus variables crudas.

  Cobertura: 4 tests nuevos en `cliente.test.ts` (`obtenerComponente`,
  `obtenerCalibracion`, `guardarCalibracion` con éxito y con 400), +
  `CalibracionPanel.test.tsx`, `ComponenteDetalle.test.tsx`,
  `IndicadoresTiles.test.tsx`. De paso se encontró y corrigió una falta de
  limpieza entre tests (`@testing-library/react`'s `cleanup()` no estaba
  registrado en `setup.ts`) que dejaba el DOM de un render anterior vivo
  para el siguiente test del mismo archivo -- invisible mientras cada test
  consultaba texto único, pero destapado por los tests de
  `CalibracionPanel`, donde "Guardar calibración" se repite en cada test.
  Verificado en vivo: reconstruí el `monitor-api` de docker compose con el
  frontend nuevo y confirmé que el bundle servido trae el código de los
  tres componentes nuevos.
- [x] **E3. Selector de instancia — construido como "vista general".** El
  backend ya expone `GET /api/v1/instancias` (`InstanciasController` +
  `RepositorioInstancias`), y el frontend arranca en `VistaInstancias` (una
  grilla con un semáforo por base) desde donde se entra a
  `DashboardInstancia`. Responde al pedido del profesor de "un dashboard
  principal donde aparezcan todas las bases de datos".

  **Ojo, sigue a medias**: hoy solo hay UNA instancia real
  (`docker-compose.yml` levanta un único Oracle y V6 siembra una sola fila),
  así que la pantalla existe pero no demuestra todavía el multi-cliente.
  Levantar una segunda instancia Oracle es lo que la convierte en una demo
  de verdad — ADR 0001 dejó el compose preparado para duplicar el servicio,
  con la advertencia de que cada contenedor de Oracle Free pesa varios GB.

  Pendientes menores anotados al construirla: no sincroniza con la URL (el
  botón "atrás" del navegador no vuelve a la vista general), y el estilo
  semáforo+barra se eligió sobre un velocímetro con aguja sin confirmarlo
  con el profesor.
- [x] **E4. Pulido de UX.** Loading skeleton (`.skeleton`, shimmer con
  `prefers-reduced-motion` respetado) reemplaza el "Cargando…" plano.
  Manejo de errores por panel: antes `refrescar()` atrapaba los fallos de
  histórico/tablespaces/alertas y los convertía en arrays vacíos
  silenciosos -- indistinguible de "de verdad no hay alertas". Ahora
  `cargarSeguro()` guarda el mensaje de error junto al valor, y
  `TablespacesPanel`/`AlertasPanel` reciben un prop `error` opcional que
  muestra un mensaje distinto (nunca el estado "vacío, todo bien").

  Cobertura: un test nuevo por panel (error vs. vacío) + `App.test.tsx`
  (nuevo: esqueleto de carga visible mientras `/salud` está pendiente, y
  un fallo de `/alertas` no tumba el hero ni se confunde con "sin
  alertas"). Verificado en vivo contra el `monitor-api` de docker compose.

## Módulo F — Alcance a decidir (no descartado, pausado)
- **F1. Cadena de bloqueos (`V$SESSION.BLOCKING_SESSION`).** Estaba en el
  prototipo aprobado; no hay recolector ni endpoint para esto todavía.
  Requiere: nuevo `RecolectorBloqueos` (o extender `RecolectorProcesos`),
  nueva vista de dominio para el grafo, endpoint REST, componente de
  visualización en el frontend (probablemente un grafo SVG, como el
  prototipo).
- **F2. Multi-tenant / onboarding de otras empresas.** La pregunta que
  planteaste al principio de la sesión sobre analizar bases de datos de
  distintas empresas, no solo la propia — la pausamos explícitamente
  ("podemos verlo posterior"). Sigue pausada; retomar solo si el profesor
  la vuelve a plantear.

---

## Orden sugerido

1. **A1-A4** (infraestructura) — desbloquea que el resto sea demostrable
   fuera de este dev machine.
2. **B1 arranca ya** (es una operación de fondo, no compite con nada más —
   conviene dejarla corriendo mientras se trabaja en otros módulos).
3. **C1-C2** (testing) — barato, da confianza antes de seguir tocando código.
4. **D1-D2** (alertas) — ya tenemos el mecanismo, solo falta cablearlo.
5. **B2-B5** (calibración real) cuando B1 tenga suficientes datos.
6. **E1-E4** (frontend) y **F1-F2** según tiempo disponible antes de la
   entrega.
