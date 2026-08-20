# Auditoría externa: defectos encontrados y dónde se corrigieron

Índice de los defectos que encontró una auditoría externa del código y en
qué commit se corrigió cada uno.

> **Sobre este documento.** Quince comentarios repartidos por el backend
> dicen *"encontrado por auditoría externa (ver `docs/`)"*, pero el informe
> original nunca se agregó al repositorio, así que esa referencia no
> apuntaba a nada. Esta tabla está **reconstruida a partir de los mensajes
> de commit** (`e34287c`, `f1a8c9b`), no es el informe original: recoge los
> hallazgos y el arreglo, no el texto ni el análisis completo del auditor.
> Si aparece el informe original, conviene guardarlo junto a este índice.

## Severidad ALTA — commit `e34287c`

| # | Defecto | Corrección |
|---|---|---|
| 1.1 | **Punto ciego CDB/PDB.** La conexión apuntaba a `FREE` (CDB$ROOT), así que `DBA_TABLESPACE_USAGE_METRICS` devolvía los tablespaces del contenedor equivocado — sin error, solo datos incorrectos. Apuntar todo a `FREEPDB1` rompía memoria y procesos: `V$PGASTAT`/`V$RESOURCE_LIMIT` son de instancia y solo se pueblan desde la raíz (verificado: *aggregate PGA target parameter* da 536870912 desde `FREE` y 0 desde `FREEPDB1`). | Dos DataSource: `oracleMonitoreado` (`FREE`) para procesos/memoria/fondo y `oracleMonitoreadoPdb` (`FREEPDB1`) solo para archivos. Ver `DataSourceConfig`, `application.yml`, `docker-compose.yml`. V7 corrige el `servicio` descriptivo sembrado por V6. |
| 1.2 | **El veto no se propagaba entre sub-indicadores.** Con la calibración por defecto, `IP_fondo=0` (vetado) + `IP_usuarios=100` con pesos 0.40/0.60 da **exactamente 40.0** — el mismo valor que el umbral de veto, y `< 40.0` es estrictamente falso. Un proceso mandatorio caído se reportaba SALUDABLE. | Campo nuevo `Indicador.vetado`, propagado con OR en `CombinadorSubIndicadores` y verificado en `MotorIndicadores` **antes** de la comparación numérica. Constructor de 3 argumentos preservado para no tocar los ~15 call sites. |
| 1.3 | **Cero timeouts de consulta en todo el backend.** Una consulta colgada bloqueaba para siempre el único hilo del planificador: el monitor dejaba de muestrear en silencio, justo el modo de fallo que un monitor de salud no puede tener. | `JdbcClienteConTimeout` (`setQueryTimeout(10s)`) para los cuatro recolectores, `oracle.jdbc.ReadTimeout=15000` como defensa a nivel de socket, y `spring.task.scheduling.pool.size=2`. |
| 1.4 | **`b1_procesos_caidos` indistinguible de "todo sano".** `COUNT(CASE WHEN paddr='00'...)` sobre un `WHERE` que no casa ninguna fila devuelve 0 — igual que si los cinco procesos mandatorios estuvieran vivos. | Se cuenta también `procesos_encontrados` y se descarta la muestra (`RecoleccionFallidaException`) si no son 5. |
| 1.5 | **UNDO y TEMP contaminaban `MAX(used_percent)`.** Ambos marcan 90-100% rutinariamente en una instancia sana (UNDO cuenta extents no expirados, TEMP no libera el high-water mark), y con el veto en 98% podían forzar `IA=0` sin que pasara nada malo. | Filtro por `DBA_TABLESPACES.CONTENTS='PERMANENT'` en el agregado y en el detalle por tablespace. Afecta también a las alertas. |

**De paso**: `JdbcRepositorioCalibracionIT` no limpiaba su fila "vigente"
entre corridas. Como comparte la base histórico con el `monitor-api` real,
había dejado el sistema en vivo con el veto **deshabilitado** y pesos
0.25/0.25/0.50 en vez de los 0.30/0.35/0.35 de ADR 0003.

## Severidad MEDIA/BAJA — commit `f1a8c9b`

| # | Defecto | Corrección |
|---|---|---|
| 2.1 | Un ciclo perdido (backend reiniciado a mitad de intervalo) producía falsos picos de "presión de PGA". | `CalculadorDelta` descarta la comparación si el intervalo entre muestras supera 3 minutos. |
| 2.2 | Reinicio de instancia no detectable con certeza. | Sin cambio de código: documentado como limitación conocida en `CalculadorDelta` (requeriría persistir `V$INSTANCE.STARTUP_TIME`). |
| 2.3 | `Calibracion` solo validaba que los pesos sumaran 1.0, así que `{0.0, 0.0, 1.0}` pasaba y podía dividir entre cero en `MotorIndicadores`. | Exige un peso por cada uno de los 3 componentes y que cada uno sea > 0. |
| 2.4 | `Estado.desdePuntuacion` no rechazaba valores fuera de `[0,100]` ni `NaN`. | Los rechaza explícitamente, con tolerancia de punto flotante (1e-6) para no romper sumas ponderadas legítimas que dan 100.00000000000001. |
| 2.5 | Una alerta de tablespace quedaba abierta para siempre si el tablespace desaparecía (renombrado o dropeado): el bucle solo evaluaba los que sí venían en la recolección actual. | `cerrarAlertasDeTablespacesQueYaNoExisten`, y se preserva el `Optional<List<...>>` en vez de colapsar fallo/vacío, para no reconciliar sobre un fallo transitorio. |
| 2.6 | Comentario falso en `DataSourceConfig`: decía que `v$session.program` filtraba `p1`/`p3`, y es imposible (`V$RESOURCE_LIMIT` viene pre-agregada). | Comentario corregido. |
| 2.7 | `/salud` devolvía el último ISBD bueno sin avisar que el planificador podía llevar varios ciclos sin correr. | Campo `IsbdDto.vetusto` (3× el intervalo de muestreo), calculado en la capa API, reflejado en `IsbdHero`. |
| 2.9 | Con 1 de 3 componentes recolectados, el ISBD podía dar SALUDABLE u ÓPTIMO. | El estado se topa en ADVERTENCIA en ese caso, salvo que ya sea CRÍTICO por veto. |

**De paso**: `JdbcRepositorioAlertasIT` dejaba alertas de prueba abiertas a
propósito y no las limpiaba — como usa la misma `InstanciaId(1)` que el
`monitor-api` real, aparecían en el sistema en vivo.

## Defectos encontrados después de la auditoría

| # | Defecto | Corrección |
|---|---|---|
| 3.1 | **Regresión introducida por el arreglo de 2.5.** Toda la evaluación de alertas de ARCHIVOS quedó dentro del `ifPresent` del detalle por tablespace. Pero `a2_datafiles_offline` sale del **agregado**, no del detalle: un fallo de `DBA_TABLESPACE_USAGE_METRICS` (consulta aparte, más pesada, ahora con timeout de 10s por 1.3) se tragaba en silencio la alerta de un datafile OFFLINE. El veto del ISBD sí disparaba, lo que lo hacía más difícil de ver: el dashboard mostraba CRÍTICO pero el panel de alertas quedaba vacío y `MONITOR_ALERTAS` sin episodio. | `evaluarAlertas` se dividió en `evaluarAlertaDatafilesOffline` (siempre que haya agregado) y `evaluarAlertasDeTablespaces` (solo si el detalle se recolectó). Dos tests de regresión en `MuestrearInstanciaServicioTest`, verificados contra el código anterior para confirmar que lo detectan. |

## Pendiente de la misma familia

Contaminación de la base compartida: los `*IT` corren contra el mismo
Postgres que el `monitor-api` real, así que cualquier test que escriba y no
limpie deja rastro en el sistema en vivo. Ya se corrigió en
`JdbcRepositorioCalibracionIT`, `JdbcRepositorioAlertasIT`,
`JdbcRepositorioInstanciasIT` y `JdbcRepositorioUmbralesIT` — pero el
patrón se va a repetir con cada IT nuevo. La solución de fondo es una base
de datos de test aparte (o Testcontainers), no acordarse de limpiar.
