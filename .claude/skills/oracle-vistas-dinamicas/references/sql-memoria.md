# SQL — Subsistema de memoria (m1–m9)

Este es el subsistema donde más fácil se construye un monitor que miente. Dos
razones: (1) alta ocupación de memoria en Oracle normalmente significa que Oracle
está funcionando bien, y (2) las estadísticas de PGA más interesantes son
**acumuladas desde el arranque de la instancia**, así que leídas como instantáneas
no detectan nada.

## Consulta principal: una fila con todo el subsistema

```sql
SELECT
    SYSTIMESTAMP AS muestreado_en,
    sga.sga_total_bytes     AS m1,
    sga.sga_libre_bytes     AS m2,
    pool.shared_pool_bytes  AS m3,
    sga.buffer_cache_bytes  AS m4,
    pga.pga_asignada        AS m5,
    pga.pga_en_uso          AS m6,
    pga.pga_maxima          AS m7,
    pga.over_alloc_acum     AS m8,
    pga.cache_hit_pct       AS m9,
    pga.pga_target          AS pga_target_bytes,
    ins.startup_time        AS arranque_instancia
FROM
    ( SELECT
        MAX(CASE WHEN name = 'Maximum SGA Size'          THEN bytes END) AS sga_total_bytes,
        MAX(CASE WHEN name = 'Free SGA Memory Available' THEN bytes END) AS sga_libre_bytes,
        MAX(CASE WHEN name = 'Buffer Cache Size'         THEN bytes END) AS buffer_cache_bytes
      FROM v$sgainfo
    ) sga,
    ( SELECT SUM(bytes) AS shared_pool_bytes
      FROM   v$sgastat
      WHERE  pool = 'shared pool'
    ) pool,
    ( SELECT
        MAX(CASE WHEN name = 'total PGA allocated'           THEN value END) AS pga_asignada,
        MAX(CASE WHEN name = 'total PGA inuse'               THEN value END) AS pga_en_uso,
        MAX(CASE WHEN name = 'maximum PGA allocated'         THEN value END) AS pga_maxima,
        MAX(CASE WHEN name = 'over allocation count'         THEN value END) AS over_alloc_acum,
        MAX(CASE WHEN name = 'cache hit percentage'          THEN value END) AS cache_hit_pct,
        MAX(CASE WHEN name = 'aggregate PGA target parameter' THEN value END) AS pga_target
      FROM v$pgastat
    ) pga,
    ( SELECT startup_time FROM v$instance ) ins;
```

**Costo:** bajo. `V$SGASTAT` tiene decenas de filas pero la agregación ocurre en la
base; no traigas la lista completa al cliente en cada muestra.

Fíjate en que la consulta trae `startup_time`. No es decorativo: es lo que permite
detectar el reinicio que invalida las deltas de m8 y m9.

---

## El problema de los acumulados (m8 y m9)

`over allocation count` y `cache hit percentage` de `V$PGASTAT` son **acumulados
desde el arranque de la instancia**. Lo que esto implica en la práctica:

- `over allocation count` solo crece. Si vale 1 500, no sabes si hubo 1 500
  sobreasignaciones hace un mes o quince en el último minuto. Como señal de salud
  actual, el valor absoluto no sirve.
- `cache hit percentage` es un promedio sobre toda la vida de la instancia. Tras
  unos días de uptime está tan promediado que un episodio grave de presión hoy lo
  mueve en décimas. Un monitor que le ponga un umbral instantáneo no disparará nunca.

**La señal está en la diferencia entre muestras consecutivas:**

```
presion_pga(t) = m8(t) − m8(t−1)
```

Si esa delta es mayor que cero, hubo sobreasignación **en ese intervalo**. Eso sí
es una señal instantánea y accionable.

Guarda igualmente el acumulado crudo: lo necesitas para calcular la delta siguiente
y para auditar. Pero **normaliza sobre la delta**.

**El caso del reinicio.** Si `m8(t) < m8(t−1)`, la instancia se reinició. Comprueba
`startup_time` para confirmarlo. En ese caso la delta no existe: márcala como
`NULL` e indica `instancia_reiniciada = 1`. Registrar una delta negativa contamina
el índice y arruina las estadísticas de calibración. Y no des por hecho que el
reinicio se detecta solo por la bajada del contador: si el reinicio ocurrió entre
dos muestras y el contador ya volvió a subir por encima del valor previo, solo
`startup_time` te lo dice.

```sql
-- Comparar con la muestra anterior, con detección de reinicio (SQL analítico)
SELECT
    muestreado_en,
    m8_over_alloc_acum,
    CASE
      WHEN LAG(arranque_instancia) OVER (ORDER BY muestreado_en) <> arranque_instancia
        THEN NULL                                   -- reinicio: delta no definida
      ELSE m8_over_alloc_acum
           - LAG(m8_over_alloc_acum) OVER (ORDER BY muestreado_en)
    END AS delta_over_alloc
FROM monitor_memoria
WHERE instancia_id = :instancia
ORDER BY muestreado_en;
```

---

## Interpretación: por qué "memoria llena" casi nunca es el problema

**SGA.** Oracle llena el buffer cache a propósito; ahí está su rendimiento. Una SGA
con poca memoria libre es una SGA haciendo su trabajo. `Free SGA Memory Available`
en `V$SGAINFO` es la memoria **todavía no asignada a ningún componente**, no memoria
"desperdiciada": en una instancia con `SGA_TARGET` bien ajustado suele ser cero y
eso es correcto. Puntuar la salud como "SGA libre / SGA total" produce un monitor
que marca rojo permanente en una instancia perfectamente sana.

Lo que sí es señal en la SGA:

```sql
-- Fragmentación / presión de shared pool: memoria libre por pool
SELECT pool, name, ROUND(bytes/1024/1024, 1) AS mb
FROM   v$sgastat
WHERE  name = 'free memory'
ORDER BY pool;

-- Redimensionamientos automáticos: si los componentes se mueven mucho,
-- la instancia está buscando un equilibrio que no encuentra
SELECT component,
       ROUND(current_size/1024/1024) AS actual_mb,
       ROUND(min_size/1024/1024)     AS min_mb,
       ROUND(max_size/1024/1024)     AS max_mb,
       oper_count,
       last_oper_type
FROM   v$memory_dynamic_components
WHERE  current_size > 0
ORDER BY current_size DESC;
```

`OPER_COUNT` alto en `V$MEMORY_DYNAMIC_COMPONENTS` indica que Oracle lleva muchas
operaciones de redimensionamiento sobre ese componente. Es un indicio bastante
elegante de configuración de memoria inestable, y es un hallazgo que da contenido
propio al informe.

**PGA.** Aquí sí hay presión real y medible. Las tres señales, en orden de valor:

1. **`Δ over allocation count > 0`** — Oracle no pudo respetar
   `PGA_AGGREGATE_TARGET` y tuvo que pedir memoria extra. Es la señal más directa.
2. **`total PGA allocated / pga_aggregate_target > 1`** — la asignación excede el
   objetivo. **No recortes este porcentaje a 100**: el exceso es exactamente la
   información que buscas.
3. **`cache hit percentage` bajo** — hubo pasadas extra sobre disco temporal porque
   las work areas no alcanzaron. Recuerda que es acumulado; para uso instantáneo,
   trabaja sobre su delta o complementa con `V$SQL_WORKAREA_HISTOGRAM`.

```sql
-- Ejecuciones de work area por tamaño: optimal / onepass / multipass
-- multipass > 0 es presión de PGA con impacto medible en tiempo de respuesta
SELECT low_optimal_size/1024   AS kb_desde,
       high_optimal_size/1024  AS kb_hasta,
       optimal_executions,
       onepass_executions,
       multipasses_executions
FROM   v$sql_workarea_histogram
WHERE  total_executions > 0
ORDER BY low_optimal_size;
```

`multipasses_executions` creciendo es la evidencia más convincente de que la PGA se
quedó corta: significa que Oracle tuvo que pasar varias veces sobre los mismos datos
en disco temporal. También es acumulado — usa la delta.

Si `PGA_AGGREGATE_TARGET` vale 0, la gestión automática de PGA está desactivada y
buena parte de este análisis no aplica. Detéctalo y dilo en el dashboard en vez de
dividir entre cero.

---

## Consulta de contexto: parámetros de memoria

Vale la pena leerlos en cada muestra: pueden cambiar con `ALTER SYSTEM` y si los
cacheas al arrancar, el monitor calculará porcentajes sobre un denominador obsoleto.

```sql
SELECT name, value, isdefault, ismodified
FROM   v$parameter
WHERE  name IN ('sga_target','sga_max_size','pga_aggregate_target',
                'pga_aggregate_limit','memory_target','memory_max_target',
                'db_cache_size','shared_pool_size');
```

`PGA_AGGREGATE_LIMIT` es distinto de `PGA_AGGREGATE_TARGET`: el *target* es un
objetivo que Oracle puede exceder, el *limit* es un tope duro a partir del cual
Oracle empieza a terminar sesiones. Acercarse al limit es mucho más grave que
exceder el target, y merece su propio umbral.

---

## Notas para CDB/PDB

SGA y PGA pertenecen a la **instancia**, no al contenedor. Consultadas desde un PDB
devuelven los valores de la instancia CDB completa; no se pueden atribuir a ese PDB.

No es un defecto del monitor, es cómo funciona Oracle multitenant. Lo correcto es
declararlo en el alcance del informe: "el subsistema de memoria mide la instancia;
en un despliegue multitenant sus indicadores no son atribuibles a un PDB
individual". Reconocer una limitación real y explicarla vale más que un número
inventado. Detalles en `cdb-pdb.md`.
