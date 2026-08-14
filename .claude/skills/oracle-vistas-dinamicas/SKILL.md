---
name: oracle-vistas-dinamicas
description: >-
  SQL de monitoreo contra las vistas dinámicas de rendimiento de Oracle
  (V$SESSION, V$PROCESS, V$RESOURCE_LIMIT, V$SGAINFO, V$SGASTAT, V$PGASTAT,
  V$DATAFILE, V$TEMPFILE, V$LOG, V$LOGFILE, DBA_TABLESPACE_USAGE_METRICS), con
  los permisos que hacen falta, las diferencias entre CDB y PDB, el costo de cada
  consulta y las trampas de interpretación de cada columna. Usa esta skill
  SIEMPRE que haya que consultar el estado interno de Oracle — procesos,
  sesiones, sesiones bloqueadas, límites de recursos, SGA, PGA, presión de
  memoria, datafiles, tempfiles, redo logs o espacio en tablespaces. Aplica tanto
  si el usuario menciona una vista V$ por su nombre como si solo describe lo que
  quiere saber ("cuántas sesiones hay", "está llena la memoria", "cuánto espacio
  queda", "hay bloqueos", "por qué va lenta la base"), y también cuando pregunte
  qué privilegios necesita un usuario de monitoreo o por qué una vista devuelve
  ORA-00942.
---

# Vistas dinámicas de Oracle para monitoreo

SQL listo para usar, con lo que cada consulta cuesta y lo que cada columna
realmente significa. La mayoría de los errores en un monitor no vienen de escribir
mal el SQL sino de interpretar bien un valor que significaba otra cosa — un
contador acumulado leído como instantáneo, un `STATUS='SYSTEM'` contado como fallo,
un `NULL` que quería decir "sano".

## Qué son y qué no son las vistas V$

Las V$ no son tablas: son proyecciones tabulares de estructuras de memoria de la
instancia. De ahí tres consecuencias que condicionan el diseño del monitor:

- **No hay consistencia de lectura.** No existe undo para ellas. Una consulta que
  toca varias filas puede ver instantes ligeramente distintos, así que dos
  contadores relacionados pueden no cuadrar al dígito. Para un monitor esto es
  aceptable; solo hay que no perseguir el fantasma.
- **Se vacían al reiniciar la instancia.** Todo lo acumulado (`MAX_UTILIZATION`,
  `over allocation count`, `cache hit percentage`) vuelve a cero. El monitor tiene
  que detectar el reinicio, no confundirlo con una mejora súbita.
- **Leerlas no es gratis.** Algunas toman latches internos. `V$SESSION` es barata;
  `V$WAIT_CHAINS` y `V$SQLAREA` pueden ser caras en una instancia ocupada. Muestrear
  cada 15 segundos algo que cuesta segundos convierte al monitor en parte del
  problema.

## Permisos: la trampa del `V_$`

El error inicial más común es `ORA-00942: table or view does not exist` al
consultar una V$ desde el usuario del monitor.

La causa: `V$SESSION` no es un objeto, es un **sinónimo público** que apunta a la
vista real `V_$SESSION`, propiedad de `SYS`. Un `GRANT SELECT ON V$SESSION` falla o
no surte efecto; hay que otorgar sobre `V_$`:

```sql
-- Crear el usuario de monitoreo, con el mínimo privilegio necesario
CREATE USER monitor IDENTIFIED BY "<clave-fuerte>";
GRANT CREATE SESSION TO monitor;

-- Opción A: sencilla, típica en un entorno de laboratorio
GRANT SELECT_CATALOG_ROLE TO monitor;

-- Opción B: granular, la correcta para producción y la que luce mejor en el informe
GRANT SELECT ON V_$SESSION            TO monitor;
GRANT SELECT ON V_$PROCESS            TO monitor;
GRANT SELECT ON V_$RESOURCE_LIMIT     TO monitor;
GRANT SELECT ON V_$SESSION_LONGOPS    TO monitor;
GRANT SELECT ON V_$SGAINFO            TO monitor;
GRANT SELECT ON V_$SGASTAT            TO monitor;
GRANT SELECT ON V_$PGASTAT            TO monitor;
GRANT SELECT ON V_$PARAMETER          TO monitor;
GRANT SELECT ON V_$DATAFILE           TO monitor;
GRANT SELECT ON V_$TEMPFILE           TO monitor;
GRANT SELECT ON V_$LOG                TO monitor;
GRANT SELECT ON V_$LOGFILE            TO monitor;
GRANT SELECT ON V_$RECOVER_FILE       TO monitor;
GRANT SELECT ON V_$INSTANCE           TO monitor;
GRANT SELECT ON V_$DATABASE           TO monitor;
GRANT SELECT ON DBA_TABLESPACE_USAGE_METRICS TO monitor;
```

`SELECT_CATALOG_ROLE` es cómodo pero concede acceso a **todo** el diccionario. Si el
proyecto se defiende ante alguien con criterio de seguridad, la opción B con la
justificación de mínimo privilegio es un punto a favor. Un detalle útil: los roles
no se aplican dentro de bloques PL/SQL con derechos de definidor, así que si el
monitor usara procedimientos almacenados haría falta el grant directo de todas
formas.

Además: si el usuario del monitor se conecta pero no ve nada, revisa que no esté
en un contenedor distinto al que crees. Ver `references/cdb-pdb.md`.

## Cómo está organizado el SQL

Las consultas están agrupadas por subsistema, cada una con su costo estimado, las
columnas que devuelve y sus trampas. Léelas cuando vayas a implementar el
recolector correspondiente:

| Archivo | Contenido |
|---|---|
| `references/sql-procesos.md` | p1–p8: procesos, sesiones, bloqueos, límites, operaciones largas |
| `references/sql-memoria.md` | m1–m9: SGA, PGA, presión y sobreasignación |
| `references/sql-archivos.md` | a1–a8: datafiles, tempfiles, redo, espacio de tablespaces |
| `references/cdb-pdb.md` | Multitenant, CON_ID, qué se puede y qué no se puede medir por PDB |
| `references/costos-y-trampas.md` | Costo de cada vista, contadores acumulados, vistas a evitar |

## Reglas que aplican a todas las consultas

**Una consulta por variable es un antipatrón.** Ocho consultas separadas para
p1–p8 significan ocho viajes de red y ocho instantes distintos, así que las
variables del mismo subsistema dejan de ser comparables entre sí. Agrupa por
subsistema en una sola consulta que devuelva una fila ancha; el SQL de las
referencias ya viene así.

**Sella cada muestra con una sola marca de tiempo.** Tómala en la base
(`SYSTIMESTAMP` dentro de la consulta), no en el cliente: si el reloj del servidor
de la aplicación y el de la base difieren —y suelen diferir— el histórico queda
desalineado y las correlaciones que hagas después serán falsas.

**Filtra `TYPE='USER'` en `V$SESSION` cuando cuentes sesiones de usuario.** Oracle
tiene decenas de sesiones de fondo (`TYPE='BACKGROUND'`) que siempre están ahí.
Contarlas infla la métrica con un número constante que no aporta señal, y hace que
los umbrales calibrados en una instancia no sirvan en otra con distinta
configuración de procesos de fondo.

**Nunca concatenes valores en el SQL.** Usa parámetros con nombre (`:instancia`).
El monitor lee del diccionario con un usuario privilegiado; es exactamente el sitio
donde una inyección haría más daño. Y como el SQL de un monitor es fijo y repetido
miles de veces, los `bind` además evitan llenar la shared pool de sentencias
distintas — el propio monitor degradaría la métrica de memoria que dice vigilar.

**Pon un timeout de consulta.** Si la instancia está enferma —que es justo cuando
el monitor importa— una consulta al diccionario puede colgarse. Sin timeout, el
recolector se queda esperando y el monitor deja de reportar precisamente durante el
incidente. Un `queryTimeout` de unos pocos segundos y una muestra marcada como
fallida valen mucho más que un dato perfecto que nunca llega.

**Registra los fallos de recolección como datos, no solo como log.** Que el monitor
no pudiera leer la instancia es información de salud de primer orden. Si el fallo
solo va al log, el dashboard muestra el último valor bueno y da la impresión
tranquilizadora de que todo sigue igual.

## Verificar antes de confiar

Antes de dar por buena una consulta contra la instancia real, comprueba estas tres
cosas — atrapan casi todos los errores de recolección:

```sql
-- 1. ¿Dónde estoy conectado realmente?
SELECT SYS_CONTEXT('USERENV','CON_NAME')  AS contenedor,
       SYS_CONTEXT('USERENV','DB_NAME')   AS base,
       SYS_CONTEXT('USERENV','SESSION_USER') AS usuario
FROM   dual;

-- 2. ¿Desde cuándo lleva arriba la instancia? (contexto para los acumulados)
SELECT instance_name, host_name, version, status, startup_time
FROM   v$instance;

-- 3. ¿Qué límites tengo, para poder interpretar los conteos?
SELECT name, value
FROM   v$parameter
WHERE  name IN ('processes','sessions','pga_aggregate_target',
                'sga_target','sga_max_size','memory_target');
```

`startup_time` es especialmente importante: guárdalo en cada muestra o al menos
compáralo con el de la muestra anterior. Es la forma limpia de detectar un reinicio
y descartar las deltas de contadores acumulados que ese reinicio invalida.

## Skills relacionadas

- `monitor-salud-oracle` — qué significa cada variable en el dominio del proyecto y
  cómo se llama en el esquema histórico.
- `diseno-de-indicadores` — qué hacer con estos números una vez leídos.
