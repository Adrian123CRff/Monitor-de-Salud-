# Glosario

Términos como se usan en este proyecto. Sirve para el informe, para la defensa y
para que el código y la documentación llamen igual a las mismas cosas.

## Arquitectura de Oracle

**Instancia vs. base de datos.** La *base de datos* es el conjunto de archivos en
disco (datafiles, control files, redo logs). La *instancia* es la memoria (SGA) más
los procesos en ejecución que dan acceso a esos archivos. El monitor observa
principalmente la instancia; los archivos son la parte que toca disco. La distinción
importa: la instancia se reinicia y sus contadores acumulados vuelven a cero, la
base de datos persiste.

**SGA — System Global Area.** Región de memoria compartida por todos los procesos
de la instancia. Sus componentes principales: *shared pool* (planes de ejecución y
diccionario), *database buffer cache* (bloques de datos leídos de disco), *large
pool*, *java pool*, *streams pool*. Que la SGA esté llena de datos es lo normal y lo
deseable: es caché, y una caché vacía es una caché desperdiciada.

**PGA — Program Global Area.** Memoria privada de cada proceso servidor. Aquí ocurren
los ordenamientos, los hash joins y las agregaciones. A diferencia de la SGA, la PGA
sí genera presión real: cuando una operación no cabe en su *work area*, Oracle la
resuelve con pasadas extra sobre disco temporal, y eso sí es degradación medible.

**PGA_AGGREGATE_TARGET.** Parámetro que fija cuánta PGA total intenta usar la
instancia. Es un objetivo, no un límite duro: Oracle puede excederlo, y cada vez que
lo hace incrementa `over allocation count`. Por eso el uso de PGA puede superar el
100 % de su target — y ese exceso es precisamente la señal que interesa.

**Tablespace.** Unidad lógica de almacenamiento. Agrupa uno o más datafiles físicos.
Las tablas viven en tablespaces, no en archivos concretos.

**Datafile / Tempfile.** El *datafile* almacena datos permanentes. El *tempfile*
almacena datos temporales (ordenamientos que no caben en PGA, tablas temporales
globales). Oracle los trata como objetos distintos y los expone en vistas distintas
(`V$DATAFILE` / `V$TEMPFILE`); si solo consultas datafiles tienes un punto ciego.

**Redo log.** Registro secuencial de todos los cambios, indispensable para la
recuperación. Se organiza en *grupos*, y cada grupo tiene uno o más *miembros*
(copias en distinto disco). Un grupo con un único miembro no tiene redundancia:
si ese archivo se pierde, se pierde la capacidad de recuperar. Es un hallazgo de
salud clásico y fácil de detectar.

**Undo.** Espacio donde Oracle guarda las versiones previas de los datos modificados,
para permitir rollback y lectura consistente. Vive en su propio tablespace y aparece
en `DBA_TABLESPACE_USAGE_METRICS` como cualquier otro.

**Vistas dinámicas de rendimiento (V$).** Vistas que exponen estructuras de memoria
de la instancia en formato tabular. No son tablas: no tienen undo ni consistencia de
lectura, así que dos filas de la misma consulta pueden reflejar instantes
ligeramente distintos. Para un monitor esto es aceptable; solo hay que no
sorprenderse cuando dos contadores relacionados no cuadran al dígito.

**Vistas del diccionario (DBA_/ALL_/USER_).** Vistas sobre tablas reales del
diccionario de datos. Sí tienen consistencia de lectura y son más caras de consultar.
`DBA_TABLESPACE_USAGE_METRICS` es de esta familia, y es la razón para muestrear
archivos con menos frecuencia que procesos.

**CDB / PDB.** Desde 12c Oracle es multitenant: un *container database* (CDB) aloja
varias *pluggable databases* (PDB). Consecuencia para el monitor: la memoria (SGA,
PGA) y los procesos son de la instancia CDB completa y no se pueden atribuir a un
PDB concreto, mientras que los tablespaces sí son por contenedor. Si monitoreas
desde un PDB, dilo en el alcance del informe.

**Sesión vs. proceso.** Un *proceso* es una entidad del sistema operativo que
atiende conexiones. Una *sesión* es una conexión lógica de un usuario. En modo
servidor dedicado hay aproximadamente una por una; en modo servidor compartido
muchas sesiones comparten pocos procesos. Por eso el monitor vigila los dos límites
por separado: `SESSIONS` suele agotarse antes que `PROCESSES`.

**Sesión bloqueada.** Sesión que espera a que otra libere un recurso. `V$SESSION`
la identifica con `BLOCKING_SESSION`. Un bloqueo breve es funcionamiento normal de
la concurrencia; uno largo es un incidente con culpable identificable.

---

## Vocabulario del monitor

**ISBD — Índice de Salud de la Base de Datos.** Número de 0 a 100 donde 100 es
salud perfecta. Combinación ponderada de IP, IM e IA.

**IP / IM / IA.** Indicadores de componente para Procesos, Memoria y Archivos.
Misma escala y misma polaridad que el ISBD: 100 = sano.

**Valor crudo.** Lo que devuelve Oracle en su unidad nativa (bytes, conteos,
porcentajes de utilización). Se persiste siempre.

**Score / puntuación.** El valor crudo traducido a la escala de salud 0–100
mediante la función de normalización de la variable. Nunca se persiste sin su
crudo al lado.

**Utilización.** Porcentaje de consumo respecto a un límite. Polaridad opuesta a
la salud: hay que invertirla antes de agregarla. Es el origen del error de
polaridad más común del proyecto.

**Umbral.** Valor del *crudo* a partir del cual una variable cambia de nivel
(`ok`, `advertencia`, `alto`, `critico`). Los umbrales se calibran con observación,
no se inventan, y cada uno registra su fuente.

**Histéresis.** Diferencia deliberada entre el umbral de entrada y el de salida de
un estado, para que una métrica que oscila alrededor del corte no genere una
avalancha de alertas que se abren y cierran.

**Veto.** Regla que fuerza el estado global a CRÍTICO cuando un componente cae bajo
su corte, independientemente de lo que diga la media ponderada. Es la garantía de
que el índice global no oculte un problema grave.

**Episodio de alerta.** Una alerta modelada con apertura y cierre en lugar de como
evento puntual, para que una condición sostenida sea una fila y no cientos.

**Calibración.** Conjunto versionado de umbrales y pesos vigente en un periodo. Cada
cálculo de índice registra con qué calibración se hizo, para que el histórico sea
interpretable después de recalibrar.

**Línea base (baseline).** Distribución de una variable observada en condiciones
normales durante un periodo suficiente. Es el insumo para fijar umbrales por
percentiles en lugar de por intuición.
