# Multitenant: qué se puede medir por PDB y qué no

Desde Oracle 12c la arquitectura por defecto es multitenant: una instancia
**CDB** (container database) aloja varias **PDB** (pluggable databases). Oracle 23ai
Free, la que probablemente uses en Docker, viene como CDB (`FREE`) con un PDB
(`FREEPDB1`). Ignorar esto produce dos síntomas típicos: consultas que devuelven
cero filas sin error, o métricas que parecen de un PDB y en realidad son de toda la
instancia.

## Dónde estoy conectado

Primera consulta a ejecutar siempre, antes de dar por buena cualquier otra:

```sql
SELECT SYS_CONTEXT('USERENV','CON_NAME')    AS contenedor,
       SYS_CONTEXT('USERENV','CON_ID')      AS con_id,
       SYS_CONTEXT('USERENV','DB_NAME')     AS base,
       SYS_CONTEXT('USERENV','SESSION_USER') AS usuario
FROM   dual;
```

- `CON_NAME = 'CDB$ROOT'` → estás en la raíz. Ves todos los contenedores.
- `CON_NAME = 'FREEPDB1'` (o similar) → estás dentro de un PDB. Solo ves lo suyo.

En cadenas de conexión JDBC, el `service_name` determina el contenedor:
`jdbc:oracle:thin:@//localhost:1521/FREE` entra a la raíz;
`jdbc:oracle:thin:@//localhost:1521/FREEPDB1` entra al PDB. Es una diferencia de
una palabra con consecuencias grandes, y es la causa habitual del
"mi consulta funciona en SQL*Plus y no en la aplicación".

## La columna CON_ID

Casi todas las V$ tienen `CON_ID`:

| CON_ID | Significado |
|---|---|
| 0 | El dato pertenece a toda la CDB o a un no-CDB |
| 1 | `CDB$ROOT` |
| 2 | `PDB$SEED` (la plantilla — normalmente hay que excluirla) |
| ≥3 | Un PDB concreto |

Consultada desde la raíz, una V$ devuelve filas de todos los contenedores. Si no
filtras por `CON_ID`, sumas datos de PDBs distintos y de la semilla, y obtienes
números que no corresponden a nada real.

```sql
-- Contenedores existentes y su estado
SELECT con_id, name, open_mode, restricted
FROM   v$containers
ORDER BY con_id;

-- Sesiones por contenedor (solo tiene sentido desde CDB$ROOT)
SELECT c.name AS pdb, COUNT(*) AS sesiones
FROM        v$session s
JOIN        v$containers c ON c.con_id = s.con_id
WHERE       s.type = 'USER'
GROUP BY    c.name
ORDER BY    sesiones DESC;
```

## Qué es medible por PDB y qué no

Esta tabla es la que evita prometer en el informe algo que Oracle no puede dar:

| Métrica | ¿Por PDB? | Nota |
|---|---|---|
| Sesiones (`V$SESSION`) | **Sí** | Filtrar por `CON_ID` |
| Procesos (`V$PROCESS`) | Parcial | Los procesos son de la instancia; se atribuyen vía sesión |
| Límites (`V$RESOURCE_LIMIT`) | **No** | `PROCESSES` y `SESSIONS` son parámetros de instancia |
| SGA (`V$SGAINFO`, `V$SGASTAT`) | **No** | La SGA es única para toda la instancia |
| PGA (`V$PGASTAT`) | **No** | Agregada a nivel de instancia |
| Datafiles (`V$DATAFILE`) | **Sí** | Cada PDB tiene los suyos |
| Tablespaces | **Sí** | `DBA_*` dentro del PDB; `CDB_*` desde la raíz |
| Redo logs (`V$LOG`) | **No** | El redo es de la CDB, compartido |

**Consecuencia de diseño.** El subsistema de memoria del monitor mide la
**instancia**, no el PDB. En un despliegue multitenant, IM no es atribuible a un
inquilino individual. No es un defecto del monitor: es la arquitectura de Oracle.

La forma correcta de manejarlo en el informe es declararlo en el alcance:

> "El monitor opera a nivel de instancia. Los indicadores de procesos y archivos
> pueden desglosarse por contenedor, mientras que los de memoria son inherentemente
> de instancia por diseño de Oracle multitenant."

Eso demuestra que entendiste la arquitectura. Inventar un IM por PDB demuestra lo
contrario, y es de las cosas que un profesor de administración de bases de datos
detecta de inmediato.

## Vistas CDB_* vs DBA_*

Desde `CDB$ROOT`, las vistas `CDB_*` son la versión multi-contenedor de las `DBA_*`,
con una columna `CON_ID` extra:

```sql
-- Espacio de todos los tablespaces de todos los PDBs (desde CDB$ROOT)
SELECT c.name AS pdb, m.tablespace_name, ROUND(m.used_percent, 2) AS usado_pct
FROM        cdb_tablespace_usage_metrics m
JOIN        v$containers c ON c.con_id = m.con_id
WHERE       c.con_id > 2                    -- excluir CDB$ROOT y PDB$SEED
ORDER BY    m.used_percent DESC;
```

Ojo con dos cosas: `CDB_*` es notablemente más cara que `DBA_*` porque consulta cada
contenedor, y requiere el privilegio `SET CONTAINER` además del `SELECT`.

## El usuario del monitor en multitenant

Dos opciones, y la elección tiene consecuencias:

**Usuario común (en CDB$ROOT).** Nombre con prefijo `C##`, existe en todos los
contenedores, puede consultar la instancia completa. Es lo adecuado si quieres
monitorear la CDB entera:

```sql
ALTER SESSION SET CONTAINER = CDB$ROOT;
CREATE USER c##monitor IDENTIFIED BY "<clave>" CONTAINER=ALL;
GRANT CREATE SESSION      TO c##monitor CONTAINER=ALL;
GRANT SET CONTAINER       TO c##monitor CONTAINER=ALL;
GRANT SELECT_CATALOG_ROLE TO c##monitor CONTAINER=ALL;
```

**Usuario local (en un PDB).** Existe solo en ese PDB, no ve las métricas de
instancia completas. Suficiente si el alcance del proyecto es un PDB:

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER monitor IDENTIFIED BY "<clave>";
GRANT CREATE SESSION TO monitor;
GRANT SELECT_CATALOG_ROLE TO monitor;
```

Para este proyecto, **el usuario común conectado a `CDB$ROOT` es la opción
recomendada**: da acceso a las tres familias de métricas sin puntos ciegos, que es
lo que la propuesta describe. El `C##` en el nombre es obligatorio para usuarios
comunes salvo que se cambie `COMMON_USER_PREFIX`, cosa que no vale la pena tocar.

## Autonomous Database: por qué conviene evitarla aquí

Si consideras Oracle Cloud Free Tier (Autonomous Transaction Processing), ten en
cuenta que Autonomous **restringe muchas vistas V$** y no da acceso a la
administración de memoria de la instancia. Buena parte del subsistema de memoria de
este proyecto simplemente no es observable ahí, porque Oracle gestiona esos recursos
y no los expone al inquilino.

Para este proyecto, **Oracle 23ai Free en Docker es la mejor opción**: acceso
completo a todas las vistas, y —tan importante como eso— la posibilidad de
**provocar condiciones de estrés** para calibrar umbrales. Sin poder llenar un
tablespace a propósito o abrir 200 sesiones, calibrar es adivinar.

```bash
docker run -d --name oracle-monitor \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=<clave> \
  gvenzl/oracle-free:slim
```

La primera arrancada tarda unos minutos. `FREEPDB1` es el PDB por defecto y
`system`/`sys` usan la contraseña que pasaste. Las variantes `-slim` y `-faststart`
del mismo repositorio arrancan bastante más rápido, lo cual se agradece cuando
estás iterando.

Un aviso práctico: la edición Free tiene topes de recursos (CPU, memoria SGA+PGA y
tamaño de datos limitados por licencia). Eso es una ventaja para calibrar —es
mucho más fácil provocar presión de memoria— pero significa que los umbrales que
calibres ahí no son trasladables tal cual a una instancia de producción. Menciónalo
en el informe: es exactamente el tipo de matiz que separa un trabajo bueno de uno
excelente.
