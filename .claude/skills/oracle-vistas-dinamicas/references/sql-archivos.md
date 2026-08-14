# SQL — Subsistema de archivos (a1–a8)

De los tres subsistemas, este es el que más probablemente genere una alerta que
evite un incidente real: un tablespace lleno detiene la base de datos, y es uno de
los pocos fallos que se ven venir con horas de antelación.

## Consulta principal: agregados del subsistema

```sql
SELECT
    SYSTIMESTAMP AS muestreado_en,
    df.datafiles_online     AS a1,
    df.datafiles_offline    AS a2,
    df.datafiles_bytes      AS a3,
    ts.peor_tablespace_pct  AS a4,
    ts.tablespaces_riesgo   AS a4_riesgo,
    ts.peor_tablespace      AS a4_nombre,
    tf.tempfiles_online     AS a5,
    tf.tempfiles_bytes      AS a5_bytes,
    rl.grupos_redo          AS a6,
    rl.min_miembros_grupo   AS a6_min_miembros,
    lf.archivos_invalidos   AS a7,
    df.datafiles_recover    AS a8
FROM
    ( SELECT
        COUNT(CASE WHEN status IN ('ONLINE','SYSTEM') THEN 1 END) AS datafiles_online,
        COUNT(CASE WHEN status IN ('OFFLINE','SYSOFF') THEN 1 END) AS datafiles_offline,
        COUNT(CASE WHEN status = 'RECOVER'            THEN 1 END) AS datafiles_recover,
        SUM(bytes)                                                AS datafiles_bytes
      FROM v$datafile
    ) df,
    ( SELECT
        ROUND(MAX(used_percent), 2)                            AS peor_tablespace_pct,
        COUNT(CASE WHEN used_percent > :umbral_riesgo THEN 1 END) AS tablespaces_riesgo,
        MAX(tablespace_name) KEEP (DENSE_RANK FIRST
            ORDER BY used_percent DESC)                        AS peor_tablespace
      FROM dba_tablespace_usage_metrics
    ) ts,
    ( SELECT
        COUNT(CASE WHEN status = 'ONLINE' THEN 1 END) AS tempfiles_online,
        SUM(bytes)                                    AS tempfiles_bytes
      FROM v$tempfile
    ) tf,
    ( SELECT
        COUNT(DISTINCT group#) AS grupos_redo,
        MIN(members)           AS min_miembros_grupo
      FROM v$log
    ) rl,
    ( SELECT COUNT(CASE WHEN status = 'INVALID' THEN 1 END) AS archivos_invalidos
      FROM v$logfile
    ) lf;
```

**Costo:** medio-alto, y todo el costo está en `DBA_TABLESPACE_USAGE_METRICS`. Es
una vista del diccionario, no una V$, y recorre estructuras de espacio reales. Con
muchos tablespaces y datafiles puede tardar cientos de milisegundos o más. Por eso
el subsistema de archivos se muestrea cada 5–15 minutos y no cada 30 segundos: el
espacio no cambia lo bastante rápido como para justificar el costo.

---

## Trampas de interpretación

**`STATUS='SYSTEM'` en `V$DATAFILE` significa online.** Es el estado normal de los
datafiles del tablespace SYSTEM. Si tu condición es `status = 'ONLINE'` a secas,
los cuentas como no-online y el monitor reporta datafiles caídos desde la primera
ejecución en una base perfectamente sana. Los valores posibles son `OFFLINE`,
`ONLINE`, `SYSTEM`, `RECOVER` y `SYSOFF`.

**`V$DATAFILE.STATUS` y `DBA_DATA_FILES.ONLINE_STATUS` no son lo mismo.** La primera
refleja el estado desde la perspectiva del control file; la segunda desde el
diccionario. Para el monitor, `V$DATAFILE` es la fuente correcta: es más barata y
es la que refleja el estado operativo actual.

**`USED_PERCENT` ya considera autoextend.** Es el porcentaje sobre `TABLESPACE_SIZE`,
que es el tamaño *máximo posible* teniendo en cuenta `AUTOEXTEND`, el espacio libre
del sistema de archivos subyacente y `MAX_PDB_STORAGE` si aplica. Es exactamente
la pregunta que interesa: *¿cuánto margen real queda?*

Esta es la razón para preferir esta vista sobre el cálculo clásico con
`DBA_DATA_FILES` + `DBA_FREE_SPACE`. Ese cálculo es laborioso, propenso a errores
y —lo importante— **ignora autoextend**: reporta 98 % lleno en un tablespace que
puede crecer diez veces más, y genera una alerta falsa cada vez. La vista cubre
tablespaces permanentes, temporales y de undo.

**Nunca promedies el uso de tablespaces.** Doce tablespaces al 40 % y uno al 99 %
promedian 45 % y suenan tranquilos, mientras la base está a punto de detenerse.
Usa el **máximo** (`a4`) y el **conteo de tablespaces en riesgo** (`a4_riesgo`).
Es la regla del veto de `monitor-salud-oracle`, aplicada dentro de un componente.

**`V$LOGFILE.STATUS` en `NULL` significa sano.** Contraintuitivo pero así es: un
miembro de redo log en uso normal tiene `STATUS` nulo. Los estados con contenido
son los problemáticos:

| STATUS | Significado |
|---|---|
| `NULL` | En uso, sano |
| `INVALID` | El archivo es inaccesible — problema real |
| `STALE` | Contenido incompleto; ocurre tras un reinicio o durante un cambio |
| `DELETED` | El miembro ya no se usa |

Un `COUNT(*) WHERE status IS NOT NULL` como métrica de "archivos con problemas"
contaría `DELETED` como fallo. Filtra explícitamente por `INVALID`, y trata `STALE`
como advertencia, no como crítico.

**`V$LOG.STATUS` no tiene estados malos en operación normal.** `UNUSED`, `CURRENT`,
`ACTIVE`, `INACTIVE`, `CLEARING` y `CLEARING_CURRENT` son todos parte del ciclo
normal. La salud del redo no está en el estado del grupo sino en dos cosas
distintas:

- **Redundancia**: `MIN(members)` sobre `V$LOG`. Un grupo con un solo miembro no
  tiene copia. Es un hallazgo de salud clásico y trivial de detectar.
- **Frecuencia de cambio de log**: si los logs rotan cada pocos minutos, están
  subdimensionados y la instancia sufre en cada `checkpoint`.

```sql
-- Frecuencia de cambio de redo log en las últimas 24 h
SELECT TO_CHAR(first_time, 'YYYY-MM-DD HH24') AS hora,
       COUNT(*) AS cambios
FROM   v$log_history
WHERE  first_time > SYSDATE - 1
GROUP BY TO_CHAR(first_time, 'YYYY-MM-DD HH24')
ORDER BY hora;
```

Más de 4–6 cambios por hora de forma sostenida es la señal clásica de redo logs
pequeños. Es un buen hallazgo para el informe porque tiene una recomendación
concreta detrás.

**Los tempfiles no están en `V$DATAFILE`.** Oracle los separa deliberadamente. Si
solo consultas datafiles, tienes un punto ciego justo donde fallan los `ORDER BY`
grandes, los hash joins y las tablas temporales globales. Y un tablespace temporal
lleno produce `ORA-01652`, que es de los errores que más desconciertan porque la
base "tiene espacio de sobra" en los tablespaces permanentes.

---

## Detalle por tablespace: para el dashboard

El agregado alimenta el índice; el detalle es lo que hace accionable la alerta.
Nadie puede hacer nada con "el peor tablespace está al 93 %" si no sabe cuál es.

```sql
SELECT
    tablespace_name,
    ROUND(used_percent, 2)                          AS usado_pct,
    ROUND(used_space  * block_size / 1024/1024/1024, 2) AS usado_gb,
    ROUND(tablespace_size * block_size / 1024/1024/1024, 2) AS maximo_gb,
    ROUND((tablespace_size - used_space) * block_size / 1024/1024/1024, 2) AS libre_gb
FROM   dba_tablespace_usage_metrics
ORDER BY used_percent DESC;
```

`USED_SPACE`, `ALLOCATION_SIZE` y `TABLESPACE_SIZE` vienen en **bloques**, no en
bytes. Multiplica por `BLOCK_SIZE` (que la propia vista te da) antes de mostrar
nada en GB. Reportar bloques como bytes es un error silencioso: los números se ven
plausibles y están mal por un factor de 8 192.

Guardar este detalle por muestra en `MONITOR_TABLESPACE` habilita algo que el
agregado no permite y que da mucho valor al proyecto: **proyección de agotamiento**.
Con la pendiente de crecimiento de las últimas semanas puedes estimar en cuántos
días un tablespace llegará al 100 %, y avisar antes de que sea urgente. Eso es
monitoreo predictivo con regresión lineal simple — un paso natural hacia la V4 y
mucho más barato de implementar de lo que suena.

---

## Archivos que necesitan recuperación

```sql
SELECT r.file#,
       d.name        AS archivo,
       d.status      AS estado_datafile,
       r.error       AS error_recuperacion,
       r.change#,
       r.time
FROM        v$recover_file r
JOIN        v$datafile     d ON d.file# = r.file#;
```

**Costo:** bajo. En una base sana devuelve cero filas. Cualquier fila aquí es
crítica por definición y debería vetar el estado global — no hay grado, un archivo
que necesita recuperación es un problema serio y presente.

---

## Modo archivelog

No está en el catálogo de variables de la V1, pero es una línea de SQL y aporta
contexto importante a cualquier discusión de recuperabilidad:

```sql
SELECT name, log_mode, open_mode, database_role, protection_mode
FROM   v$database;
```

Una base en `NOARCHIVELOG` no puede recuperarse a un punto en el tiempo. No es un
problema de salud operativa inmediata, pero sí una condición estructural que
merece aparecer en el dashboard como información permanente — y es material directo
para la V3 del proyecto.
