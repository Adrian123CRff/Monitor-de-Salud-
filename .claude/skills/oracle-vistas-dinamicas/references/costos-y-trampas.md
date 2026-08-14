# Costo de las consultas y trampas transversales

## Tabla de costos

Un monitor que degrada la instancia que observa es peor que no tener monitor. Esta
tabla es la base para decidir la frecuencia de cada recolector.

| Vista | Costo | Frecuencia segura | Por qué |
|---|---|---|---|
| `V$INSTANCE`, `V$DATABASE` | Trivial | Cualquiera | Una fila, memoria pura |
| `V$PARAMETER` | Bajo | 1–5 min | Cientos de filas, sin latches costosos |
| `V$SESSION` | Bajo | 10–30 s | Lectura de memoria; agrega en la base, no en el cliente |
| `V$RESOURCE_LIMIT` | Bajo | 10–30 s | Pocas filas |
| `V$SESSION_LONGOPS` | Bajo | 30 s | Normalmente pocas filas |
| `V$SGAINFO` | Bajo | 30–60 s | Una docena de filas |
| `V$PGASTAT` | Bajo | 30–60 s | Una docena de filas |
| `V$SGASTAT` | Medio | 60 s | Decenas o cientos de filas; agrega por pool en SQL |
| `V$MEMORY_DYNAMIC_COMPONENTS` | Medio | 60 s | Pocas filas pero lectura más pesada |
| `V$DATAFILE`, `V$TEMPFILE` | Bajo | 1–5 min | Tantas filas como archivos |
| `V$LOG`, `V$LOGFILE` | Bajo | 1–5 min | Pocas filas |
| `V$LOG_HISTORY` | Medio | 15 min | Crece con el tiempo; filtra siempre por fecha |
| `DBA_TABLESPACE_USAGE_METRICS` | **Alto** | **5–15 min** | Vista del diccionario; recorre estructuras de espacio |
| `CDB_TABLESPACE_USAGE_METRICS` | **Muy alto** | 15–30 min | Lo anterior, por cada contenedor |
| `V$SQL_WORKAREA_HISTOGRAM` | Medio | 5 min | Pocas filas, lectura moderada |
| `V$WAIT_CHAINS` | **Muy alto** | **Bajo demanda** | Recorre cadenas de espera tomando latches |
| `V$SQLAREA`, `V$SQL` | **Muy alto** | **Bajo demanda** | Miles de filas; latches de shared pool |

## El patrón: muestreo barato continuo, diagnóstico caro puntual

La consecuencia práctica de esa tabla, y una decisión de arquitectura que vale la
pena escribir como ADR:

- El **bucle de muestreo** solo usa consultas de costo bajo. Corre siempre, a ritmo
  fijo, y su costo es predecible.
- El **diagnóstico** usa las consultas caras, se dispara solo cuando una métrica
  barata cruza un umbral, y se ejecuta una vez por episodio.

Ejemplo concreto: `V$SESSION` cuenta bloqueos cada 30 segundos (barato). Cuando el
conteo supera el umbral, el backend ejecuta **una vez** `V$WAIT_CHAINS` para
construir la cadena completa y adjuntarla a la alerta. El usuario recibe un
diagnóstico rico sin que la instancia pague ese costo 2 880 veces al día.

Este patrón tiene un nombre reconocible en la literatura de observabilidad
—*sampling* barato con *drill-down* bajo demanda— y citarlo así en el informe le da
respaldo.

## Trampas transversales

### 1. Contadores acumulados leídos como instantáneos

El error más caro del proyecto, y afecta a varias métricas:

| Métrica | Vista | Cómo usarla |
|---|---|---|
| `MAX_UTILIZATION` | `V$RESOURCE_LIMIT` | Dimensionamiento e informe, no índice |
| `maximum PGA allocated` | `V$PGASTAT` | Igual |
| `over allocation count` | `V$PGASTAT` | **Delta entre muestras** |
| `cache hit percentage` | `V$PGASTAT` | Delta, o solo como contexto |
| `*_executions` | `V$SQL_WORKAREA_HISTOGRAM` | **Delta entre muestras** |

Regla general: si el valor **nunca baja** mientras la instancia esté arriba, es
acumulado. Antes de meter cualquier métrica nueva en el índice, obsérvala unos
minutos: si solo crece, necesita delta.

### 2. El reinicio de la instancia

Todos los acumulados vuelven a cero al reiniciar. Dos consecuencias:

- Una delta negativa es señal de reinicio, no de mejora.
- Puede haber un reinicio **entre dos muestras** sin que la delta salga negativa
  (si el contador ya volvió a superar el valor previo). Por eso hay que comparar
  `V$INSTANCE.STARTUP_TIME`, no solo el signo de la delta.

Guarda `startup_time` en cada muestra. Cuesta una columna y resuelve el problema
entero.

### 3. Falta de consistencia de lectura entre vistas

Las V$ no tienen undo. Dos consultas separadas ven instantes distintos, y hasta
una sola consulta que recorre varias filas puede verlos. Si p1 y p3 salen de
consultas distintas, comparar su relación es comparar dos momentos.

Mitigación: **una consulta por subsistema**, con `SYSTIMESTAMP` dentro de la propia
consulta como sello de la muestra. No es consistencia real —Oracle no la ofrece
aquí— pero acota la ventana a milisegundos en vez de a segundos.

### 4. Reloj del cliente vs. reloj de la base

Si el sello de tiempo lo pone la aplicación y los relojes difieren —y difieren
casi siempre, sobre todo entre contenedores—, el histórico queda desalineado y
cualquier correlación temporal que hagas después es falsa. Toma `SYSTIMESTAMP` en
la base. Y si el monitor vigila varias instancias, guarda la zona horaria: comparar
dos instancias con sellos en zonas distintas produce gráficos incomprensibles.

### 5. `ORA-00942` con vistas V$

`V$SESSION` es un sinónimo público sobre `V_$SESSION`. El grant va sobre `V_$`.
Detalles en el SKILL.md principal. Segunda causa habitual: estar conectado a un
contenedor donde el usuario no existe.

### 6. Consultas sin timeout

Cuando la instancia está enferma —exactamente cuando el monitor importa— una
consulta al diccionario puede colgarse indefinidamente. Sin timeout, el hilo del
recolector se queda esperando, el siguiente muestreo se acumula detrás y el monitor
deja de reportar durante el incidente que debía detectar.

Configura un `queryTimeout` (en Spring, `spring.jpa.properties.javax.persistence.query.timeout`
o `queryTimeout` en el `JdbcTemplate`) de pocos segundos, y **registra la muestra
fallida como dato**, no solo en el log. Que el monitor no pudiera leer la instancia
es información de salud de primer orden; si solo va al log, el dashboard muestra el
último valor bueno y transmite calma justo cuando no debe.

### 7. El monitor contaminando su propia métrica

Cada conexión del monitor es una sesión y un proceso más en `V$SESSION` y
`V$RESOURCE_LIMIT`. Con un pool de 10 conexiones estás añadiendo 10 a p1 y p3.

No es grave, pero sí conviene: (a) mantener el pool del monitor pequeño —2 o 3
conexiones bastan para muestrear—, (b) poner un `program`/`client_info`
identificable en la conexión, y (c) mencionarlo en el informe. Un monitor que
reconoce y cuantifica su propio efecto observador es un monitor bien pensado.

```java
// Identificar las sesiones del monitor para poder excluirlas y depurar
props.setProperty("v$session.program",  "monitor-salud-oracle");
props.setProperty("v$session.osuser",   "monitor");
```

Y si quieres cerrar el círculo, excluye tus propias sesiones del conteo:

```sql
WHERE type = 'USER'
  AND NVL(program, '-') <> 'monitor-salud-oracle'
```

Documenta esa decisión: es discutible si el monitor debe contarse a sí mismo, y
tener una postura razonada vale más que cualquiera de las dos opciones.

### 8. Bloques vs. bytes

`DBA_TABLESPACE_USAGE_METRICS` devuelve `USED_SPACE`, `ALLOCATION_SIZE` y
`TABLESPACE_SIZE` en **bloques**. Multiplica por `BLOCK_SIZE` antes de mostrar GB.
Es un error silencioso: los números se ven plausibles y están mal por un factor de
8 192.

### 9. Concatenar en vez de usar bind variables

Además del riesgo obvio de inyección —agravado porque el monitor se conecta con un
usuario privilegiado—, hay un efecto secundario elegante: cada literal distinto
genera una sentencia distinta en la shared pool. Un monitor que ejecuta miles de
consultas concatenadas al día llena la shared pool de basura y **degrada la métrica
de memoria que dice vigilar**. Con `bind` se reutiliza el plan. Es un argumento que
vale la pena incluir en el informe porque conecta seguridad con rendimiento.

## Checklist antes de dar por buena una consulta nueva

1. ¿La probaste contra la instancia real, no solo mentalmente?
2. ¿Sabes si alguno de sus valores es acumulado?
3. ¿Está en la tabla de costos, o mediste cuánto tarda?
4. ¿Devuelve algo distinto desde `CDB$ROOT` que desde un PDB?
5. ¿Usa `bind variables`?
6. ¿Tiene timeout?
7. ¿Qué devuelve cuando no hay filas — cero, `NULL`, o nada? ¿Lo maneja el
   recolector?
8. ¿Las unidades son las que crees (bloques, bytes, centésimas, porcentajes)?

La 7 se olvida siempre. `SUM()` sobre cero filas devuelve `NULL`, no 0; `COUNT()`
devuelve 0. Si el recolector convierte ese `NULL` en 0 sin pensarlo, un fallo de
lectura se vuelve indistinguible de un valor legítimamente nulo — y el índice
registra salud perfecta justo cuando dejó de ver la base.
