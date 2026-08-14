# SQL — Subsistema de procesos (p1–p8)

## Consulta principal: una fila con todo el subsistema

Devuelve p1–p8 más los límites vigentes, en un solo viaje y un solo instante.

```sql
SELECT
    SYSTIMESTAMP AS muestreado_en,
    lim.procesos_actuales      AS p1,
    lim.procesos_maximos       AS p2,
    lim.sesiones_actuales      AS p3,
    ses.activas                AS p4,
    ses.inactivas              AS p5,
    ses.bloqueadas             AS p6,
    lop.operaciones_largas     AS p7,
    lim.peor_utilizacion_pct   AS p8,
    lim.limite_procesos,
    lim.limite_sesiones,
    ses.bloqueo_max_seg
FROM
    -- Límites de recursos: p1, p2, p3, p8
    ( SELECT
        MAX(CASE WHEN resource_name = 'processes' THEN current_utilization END) AS procesos_actuales,
        MAX(CASE WHEN resource_name = 'processes' THEN max_utilization     END) AS procesos_maximos,
        MAX(CASE WHEN resource_name = 'sessions'  THEN current_utilization END) AS sesiones_actuales,
        MAX(CASE WHEN resource_name = 'processes'
                 THEN CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1') THEN NULL
                           ELSE TO_NUMBER(TRIM(limit_value)) END END)           AS limite_procesos,
        MAX(CASE WHEN resource_name = 'sessions'
                 THEN CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1') THEN NULL
                           ELSE TO_NUMBER(TRIM(limit_value)) END END)           AS limite_sesiones,
        ROUND(MAX(CASE WHEN TRIM(limit_value) IN ('UNLIMITED','-1')
                            OR TO_NUMBER(TRIM(limit_value)) = 0 THEN 0
                       ELSE current_utilization * 100
                            / TO_NUMBER(TRIM(limit_value)) END), 2)             AS peor_utilizacion_pct
      FROM v$resource_limit
      WHERE resource_name IN ('processes','sessions','transactions',
                              'enqueue_locks','enqueue_resources')
    ) lim,
    -- Sesiones por estado: p4, p5, p6
    ( SELECT
        COUNT(CASE WHEN status = 'ACTIVE'   THEN 1 END)              AS activas,
        COUNT(CASE WHEN status = 'INACTIVE' THEN 1 END)              AS inactivas,
        COUNT(CASE WHEN blocking_session IS NOT NULL THEN 1 END)     AS bloqueadas,
        NVL(MAX(CASE WHEN blocking_session IS NOT NULL
                     THEN seconds_in_wait END), 0)                   AS bloqueo_max_seg
      FROM v$session
      WHERE type = 'USER'
    ) ses,
    -- Operaciones prolongadas en curso: p7
    ( SELECT COUNT(*) AS operaciones_largas
      FROM v$session_longops
      WHERE time_remaining > 0
    ) lop;
```

**Costo:** bajo. Las tres vistas son lecturas de memoria baratas. Segura cada 15–30 s.

### Trampas de esta consulta

**`LIMIT_VALUE` es `VARCHAR2`.** Puede valer `'UNLIMITED'`, venir con espacios de
relleno, o ser `'-1'` en algunas versiones. `TO_NUMBER` directo revienta con
`ORA-01722` en el peor momento. De ahí el `TRIM` y el `CASE`. Con límite ilimitado
la utilización no está definida: devolver `NULL` o `0` es correcto; devolver un
número inventado no.

**`MAX_UTILIZATION` (p2) es acumulado desde el arranque.** Solo sube. Es útil para
dimensionar y para el informe ("el pico histórico llegó al 78 % del límite"), pero
si lo metes en el índice como si fuera instantáneo, un pico de hace tres semanas
mantiene el índice degradado para siempre.

**`TYPE='USER'` es obligatorio.** Sin ese filtro cuentas también los procesos de
fondo de Oracle (`PMON`, `SMON`, `LGWR`, los `Wnnn`…), que en 23ai son fácilmente
50–100 sesiones permanentes. El conteo queda inflado por una constante que depende
de la configuración de la instancia, y los umbrales dejan de ser portables.

**`STATUS='ACTIVE'` no significa "trabajando".** Significa que la sesión está dentro
de una llamada a la base. Una sesión esperando un bloqueo también aparece como
ACTIVE. Por eso p4 sola no basta y p6 (bloqueadas) es la variable que de verdad
discrimina.

**`SECONDS_IN_WAIT` con `blocking_session` da la duración del bloqueo.** Es la
diferencia entre "hay 3 bloqueos" (ruido de concurrencia normal) y "hay 3 bloqueos,
el más antiguo lleva 400 segundos" (incidente). Recolectar solo el conteo desperdicia
la mitad de la señal por el mismo costo.

---

## Detalle de bloqueos: para el panel de alertas

El conteo alimenta el índice; para que la alerta sea accionable hace falta saber
quién bloquea a quién.

```sql
SELECT
    bloqueada.sid            AS sid_bloqueada,
    bloqueada.username       AS usuario_bloqueado,
    bloqueada.seconds_in_wait AS segundos_esperando,
    bloqueada.event          AS evento_espera,
    bloqueada.sql_id         AS sql_bloqueado,
    bloqueante.sid           AS sid_bloqueante,
    bloqueante.username      AS usuario_bloqueante,
    bloqueante.status        AS estado_bloqueante,
    bloqueante.program       AS programa_bloqueante,
    bloqueante.machine       AS maquina_bloqueante
FROM        v$session bloqueada
LEFT JOIN   v$session bloqueante
       ON   bloqueante.sid = bloqueada.blocking_session
WHERE       bloqueada.blocking_session IS NOT NULL
ORDER BY    bloqueada.seconds_in_wait DESC;
```

**Costo:** bajo, salvo con cientos de sesiones bloqueadas — y en ese caso ya tienes
un incidente mayor que el costo de la consulta.

`BLOCKING_SESSION_STATUS` merece atención: `VALID` es el caso normal, pero también
puede ser `UNKNOWN`, `NO HOLDER` o `GLOBAL`. En un RAC el bloqueante puede estar en
otra instancia, y ahí `BLOCKING_INSTANCE` es la columna que hace falta. Para un
monitor de instancia única, filtrar por `VALID` evita falsos positivos.

Un detalle de interpretación: el bloqueante suele aparecer con `STATUS='INACTIVE'`.
No es un error de la consulta — el caso clásico es alguien que hizo un `UPDATE`, no
hizo `COMMIT` y se fue a almorzar. La sesión no está haciendo nada y por eso mismo
está bloqueando a las demás. Que el dashboard muestre `program` y `machine` del
bloqueante convierte la alerta en algo sobre lo que se puede actuar.

---

## Detalle de operaciones largas

```sql
SELECT
    sid,
    opname,
    target,
    ROUND(sofar / NULLIF(totalwork, 0) * 100, 1) AS avance_pct,
    time_remaining                                AS segundos_restantes,
    elapsed_seconds                               AS segundos_transcurridos,
    message
FROM   v$session_longops
WHERE  time_remaining > 0
ORDER BY time_remaining DESC;
```

**Costo:** bajo.

Aquí aparecen operaciones legítimas y deseables: `CREATE INDEX`, `RMAN backup`,
`Table Scan` de un reporte grande. No las penalices por existir. Su valor está en
explicar otras métricas: un pico de PGA y de I/O con un `CREATE INDEX` en curso
tiene una causa conocida y benigna. Filtrar por `OPNAME` te permite distinguir
mantenimiento planificado de consultas descontroladas.

---

## Distribución de espera: contexto para el informe

No forma parte de la V1, pero una sola consulta te da material excelente para la
sección de análisis:

```sql
SELECT
    NVL(wait_class, 'CPU')  AS clase_espera,
    COUNT(*)                AS sesiones
FROM   v$session
WHERE  type = 'USER'
  AND  status = 'ACTIVE'
GROUP BY NVL(wait_class, 'CPU')
ORDER BY sesiones DESC;
```

Las clases (`Concurrency`, `Application`, `User I/O`, `Configuration`…) dicen *en
qué* se va el tiempo, no solo cuánto. `Concurrency` alto apunta a bloqueos,
`User I/O` alto a disco, `Configuration` a redo logs mal dimensionados. Es la puerta
natural hacia la V2 del proyecto.

---

## Vistas a evitar en el bucle de muestreo

**`V$WAIT_CHAINS`** construye la cadena completa de esperas recorriendo estructuras
internas. Es excelente para diagnosticar un bloqueo enredado y demasiado cara para
ejecutarse cada 30 segundos. Úsala **bajo demanda**: cuando p6 supere el umbral,
que el backend la consulte una vez para enriquecer la alerta. Ese patrón —muestreo
barato continuo, diagnóstico caro puntual— es el correcto y vale la pena
explicarlo en el informe.

**`V$SQLAREA` y `V$SQL`** pueden tener decenas de miles de filas en una instancia
activa, y recorrerlas toma latches de la shared pool. Quedan fuera de la V1; en la
V2, siempre con `ROWNUM`/`FETCH FIRST` y ordenadas por el criterio que interese.
