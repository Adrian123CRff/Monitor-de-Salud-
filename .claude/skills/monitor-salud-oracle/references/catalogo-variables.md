# Catálogo de variables del monitor

Las 25 variables de la V1, agrupadas por subsistema. Para cada una: qué mide, de
dónde sale, en qué unidad, hacia dónde va la salud y qué trampa tiene.

**Columna "Dirección"**: `↓ mejor` = valores altos son malos (hay que invertir al
normalizar). `↑ mejor` = valores altos son buenos. `= esperado` = solo importa si
se desvía de un valor esperado.

**Índice**

- [Procesos (p1–p8)](#procesos-p1p8)
- [Memoria (m1–m9)](#memoria-m1m9)
- [Archivos (a1–a8)](#archivos-a1a8)
- [Variables derivadas](#variables-derivadas-no-se-leen-se-calculan)
- [Frecuencias de muestreo](#frecuencias-de-muestreo-sugeridas)

---

## Procesos (p1–p8)

| Var | Nombre | Origen | Unidad | Dirección |
|---|---|---|---|---|
| p1 | Procesos actuales | `V$RESOURCE_LIMIT` (`processes`) → `CURRENT_UTILIZATION` | conteo | ↓ mejor |
| p2 | Procesos máximos alcanzados | `V$RESOURCE_LIMIT` (`processes`) → `MAX_UTILIZATION` | conteo | ↓ mejor |
| p3 | Sesiones actuales | `V$RESOURCE_LIMIT` (`sessions`) → `CURRENT_UTILIZATION` | conteo | ↓ mejor |
| p4 | Sesiones activas | `V$SESSION` con `STATUS='ACTIVE'`, `TYPE='USER'` | conteo | ↓ mejor |
| p5 | Sesiones inactivas | `V$SESSION` con `STATUS='INACTIVE'`, `TYPE='USER'` | conteo | = esperado |
| p6 | Sesiones bloqueadas | `V$SESSION` con `BLOCKING_SESSION IS NOT NULL` | conteo | ↓ mejor |
| p7 | Operaciones prolongadas | `V$SESSION_LONGOPS` con `TIME_REMAINING > 0` | conteo | ↓ mejor |
| p8 | Utilización de límites de recursos | `V$RESOURCE_LIMIT`, peor `CURRENT_UTILIZATION / LIMIT_VALUE` | % | ↓ mejor |

**Trampas**

- **p1/p3 sin su límite no significan nada.** 180 procesos es sano con
  `PROCESSES=1000` y crítico con `PROCESSES=200`. Guarda siempre el par
  `(current_utilization, limit_value)`, no solo el conteo. El límite puede cambiar
  con un `ALTER SYSTEM`, así que léelo en cada muestra en vez de cachearlo al arrancar.
- **p2 es acumulado desde el arranque de la instancia**, no un valor instantáneo.
  Solo sube. No lo uses como señal de salud actual: sirve para dimensionar
  (`¿el pico histórico se acerca al límite?`) y para el informe. Si lo metes en el
  índice tal cual, el índice se degrada permanentemente tras cualquier pico puntual
  y nunca se recupera hasta un reinicio.
- **p5 alto no es malo por sí solo.** Un pool de conexiones sano tiene muchas
  sesiones inactivas: esa es exactamente su función. La señal útil no es la
  cantidad, es la proporción y su tendencia: sesiones inactivas que crecen de forma
  monótona y no bajan sugieren fuga de conexiones en la aplicación. Modélalo como
  desviación respecto a la línea base, no como "más = peor".
- **p6 es la variable más valiosa del subsistema y merece un peso alto.** Un bloqueo
  es un problema real, presente y con responsable identificable, no una tendencia.
  Además de contarlos, guarda la duración máxima del bloqueo
  (`V$SESSION.SECONDS_IN_WAIT` de la sesión bloqueada): un bloqueo de 2 segundos es
  ruido, uno de 5 minutos es un incidente.
- **p7 cuenta operaciones largas legítimas.** Un `CREATE INDEX` o un backup aparecen
  aquí y no son un problema. Úsala como contexto para interpretar las demás, o
  filtra por `OPNAME`, no como penalización directa.
- **p8 requiere parsear texto.** `LIMIT_VALUE` e `INITIAL_ALLOCATION` son
  `VARCHAR2` y pueden valer `'UNLIMITED'` o venir con espacios de relleno. Convierte
  con cuidado y trata `UNLIMITED` como "sin restricción" (utilización = 0), nunca
  como error ni como 0 en el denominador.

---

## Memoria (m1–m9)

| Var | Nombre | Origen | Unidad | Dirección |
|---|---|---|---|---|
| m1 | Tamaño de SGA | `V$SGAINFO` → `Maximum SGA Size` | bytes | contexto |
| m2 | Memoria libre de SGA | `V$SGAINFO` → `Free SGA Memory Available` | bytes | contexto |
| m3 | Uso de Shared Pool | `V$SGASTAT` pool `shared pool` | bytes | contexto |
| m4 | Uso de Buffer Cache | `V$SGAINFO` → `Buffer Cache Size` | bytes | contexto |
| m5 | PGA asignada | `V$PGASTAT` → `total PGA allocated` | bytes | contexto |
| m6 | PGA en uso | `V$PGASTAT` → `total PGA inuse` | bytes | contexto |
| m7 | PGA máxima | `V$PGASTAT` → `maximum PGA allocated` | bytes | ↓ mejor |
| m8 | Over-allocation count | `V$PGASTAT` → `over allocation count` | conteo acumulado | ↓ mejor |
| m9 | Cache hit de PGA | `V$PGASTAT` → `cache hit percentage` | % acumulado | ↑ mejor |

**Trampas — este es el subsistema donde es más fácil equivocarse**

- **Memoria alta ≠ mala salud.** Oracle está *diseñado* para llenar la SGA: un
  buffer cache al 95 % con buen hit ratio es Oracle funcionando bien. Aplicar
  ingenuamente "más uso = peor salud" produce un monitor que grita cuando todo está
  perfecto. El documento de propuesta lo advierte explícitamente en la sección 12.
  Modela **presión** (¿Oracle está pidiendo más de lo que tiene?), no **ocupación**.
- **m8 y m9 son acumulados desde el arranque de la instancia.** Este es el error
  más caro del subsistema. `over allocation count` solo crece y `cache hit
  percentage` es un promedio histórico que después de unos días se vuelve casi
  inmóvil: un episodio grave de presión de PGA hoy mueve ese porcentaje en
  décimas y el monitor no ve nada.

  La señal real está en la **diferencia entre muestras consecutivas**:

  ```
  Δ over_allocation = m8(t) − m8(t−1)     -- >0 en el intervalo = presión AHORA
  ```

  Guarda el crudo acumulado en la tabla (lo necesitas para calcular la delta y para
  auditoría) pero **normaliza sobre la delta**. Y contempla el reinicio de la
  instancia: si `m8(t) < m8(t−1)`, la instancia se reinició; descarta esa delta en
  vez de registrar un número negativo.
- **m1–m6 son contexto, no puntuación.** Individualmente no dicen si hay problema.
  Lo que sí puntúa son razones derivadas — ver *Variables derivadas* abajo.
- **`V$SGASTAT` es una lista larga** (decenas de filas por pool). No la guardes
  entera en cada muestra: agrega por pool en el SQL y persiste los agregados.
- **En un CDB, PGA y SGA son de la instancia completa**, no del PDB. No las puedes
  atribuir a un contenedor. Si monitoreas un PDB, dilo en el informe: es una
  limitación honesta del alcance, no un defecto.

---

## Archivos (a1–a8)

| Var | Nombre | Origen | Unidad | Dirección |
|---|---|---|---|---|
| a1 | Datafiles online | `V$DATAFILE` con `STATUS IN ('ONLINE','SYSTEM')` | conteo | contexto |
| a2 | Datafiles offline | `V$DATAFILE` con `STATUS='OFFLINE'` | conteo | ↓ mejor |
| a3 | Tamaño de datafiles | `V$DATAFILE` → `SUM(BYTES)` | bytes | contexto |
| a4 | Espacio de tablespaces | `DBA_TABLESPACE_USAGE_METRICS` → `USED_PERCENT` | % | ↓ mejor |
| a5 | Tempfiles | `V$TEMPFILE` → estado y `BYTES` | conteo/bytes | ↓ mejor |
| a6 | Redo logs | `V$LOG` + `V$LOGFILE` → estado de grupos y miembros | conteo | ↓ mejor |
| a7 | Archivos inválidos | `V$LOGFILE` con `STATUS='INVALID'` | conteo | ↓ mejor |
| a8 | Archivos inaccesibles | `V$DATAFILE` con `STATUS='RECOVER'` + `V$RECOVER_FILE` | conteo | ↓ mejor |

**Trampas**

- **a4 es la variable más importante de todo el monitor** y probablemente la que
  más veces va a disparar una alerta real. Un tablespace lleno detiene la base de
  datos; es de los pocos fallos que un DBA puede prevenir con horas de antelación.
  Dale el peso que merece dentro de IA y usa el **peor tablespace**, no el promedio:
  promediar 12 tablespaces sanos con uno al 99 % da un número tranquilizador y
  falso. Esa es la regla del veto de la sección 6 del SKILL.md, aplicada dentro de
  un componente.
- **`USED_PERCENT` de `DBA_TABLESPACE_USAGE_METRICS` ya considera autoextend**: es
  el porcentaje sobre el tamaño *máximo posible*, no sobre el tamaño actual. Es
  justo lo que quieres, y es la razón para preferir esta vista sobre calcular
  a mano con `DBA_DATA_FILES` + `DBA_FREE_SPACE` (cálculo clásico, fácil de
  equivocar, y que ignora autoextend). La vista cubre tablespaces permanentes,
  temporales y de undo.
- **`STATUS='SYSTEM'` en `V$DATAFILE` significa online**, no un estado especial ni
  un error. Es el estado normal de los datafiles del tablespace SYSTEM. Contarlos
  como "no online" es un falso positivo garantizado en la primera ejecución.
- **`V$LOGFILE.STATUS` en `NULL` significa que el miembro está en uso y sano.**
  Contraintuitivo. Los estados problemáticos son `INVALID` (inaccesible) y `STALE`
  (contenido incompleto); `DELETED` es un miembro que ya no se usa.
- **`V$LOG.STATUS` no tiene estados "malos" en operación normal**: `CURRENT`,
  `ACTIVE`, `INACTIVE` y `UNUSED` son todos normales. La señal de salud en redo no
  es el estado del grupo sino el **número de miembros por grupo** (un grupo con un
  solo miembro no tiene redundancia) y la **frecuencia de cambio de log** (cambios
  cada pocos minutos indican redo logs subdimensionados).
- **a5, los tempfiles, no aparecen en `V$DATAFILE`.** Oracle los separa
  deliberadamente. Si solo consultas `V$DATAFILE` el monitor tendrá un punto ciego
  justo donde se rompen los `ORDER BY` y los hash joins grandes.

---

## Variables derivadas (no se leen, se calculan)

Estas son las que realmente alimentan los indicadores. Nombrarlas aparte evita que
alguien intente normalizar bytes crudos.

| Derivada | Fórmula | Comentario |
|---|---|---|
| `util_procesos_pct` | `p1 / limite_procesos × 100` | La utilización de la sección 7 de la propuesta |
| `util_sesiones_pct` | `p3 / limite_sesiones × 100` | Suele saturarse antes que procesos |
| `ratio_bloqueo` | `p6 / max(p4, 1)` | Fracción de actividad bloqueada |
| `presion_pga` | `Δ m8` por intervalo | Señal instantánea de sobreasignación |
| `pga_uso_pct` | `m5 / pga_aggregate_target × 100` | Puede pasar de 100: no lo recortes, es la señal |
| `sga_libre_pct` | `m2 / m1 × 100` | Poca libre no es malo por sí solo |
| `peor_tablespace_pct` | `MAX(a4)` sobre todos | Nunca el promedio |
| `tablespaces_en_riesgo` | conteo con `USED_PERCENT > umbral` | Complementa al peor |
| `redundancia_redo` | `MIN(miembros por grupo)` | 1 = sin redundancia |

---

## Frecuencias de muestreo sugeridas

No todo cambia al mismo ritmo, y consultar espacio cada 15 segundos es caro sin
aportar nada: un tablespace no pasa del 60 % al 95 % entre dos muestras.

| Subsistema | Frecuencia | Razón |
|---|---|---|
| Procesos | 15–30 s | Sesiones y bloqueos son volátiles; un bloqueo puede nacer y morir en un minuto |
| Memoria | 60 s | La presión de PGA se acumula en minutos, no en segundos |
| Archivos | 5–15 min | El espacio cambia lento y `DBA_TABLESPACE_USAGE_METRICS` es la consulta más cara del conjunto |

El ISBD se recalcula con la muestra más frecuente, reutilizando el último valor
conocido de los subsistemas lentos. Guarda la marca de tiempo de cada muestra por
separado para poder explicar en el informe con qué antigüedad de dato se calculó
cada índice.
