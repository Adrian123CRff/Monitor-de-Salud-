import type { AyudaVariable } from './tipos';

/**
 * Ficha de cada variable, en lenguaje llano.
 *
 * Traducido desde `.claude/skills/monitor-salud-oracle/references/catalogo-variables.md`.
 * Las "trampas" de ese documento son la fuente del campo `trampa` -- son
 * conocimiento real de por qué un número se malinterpreta, no relleno.
 *
 * Ninguna ficha escribe un umbral: los límites llegan por props desde el
 * backend (ver DetalleComponenteDto.VariableDto) para que sigan siendo ciertos
 * después de calibrar.
 */
export const AYUDA_VARIABLES: Record<string, AyudaVariable> = {
  // ---------------------------------------------------------------- procesos
  util_procesos_pct: {
    titulo: 'Uso de procesos',
    que: 'Qué porcentaje del cupo de procesos de Oracle está ocupado ahora mismo. Oracle no puede abrir procesos infinitos: tiene un tope configurado.',
    porQue: 'Si se llega al tope, Oracle deja de aceptar conexiones nuevas. La base sigue viva pero nadie más puede entrar, y desde afuera se ve igual que si estuviera caída.',
    siFalla: 'Revisar si hay una aplicación abriendo conexiones y no cerrándolas. Si el uso es legítimo y sostenido, hay que subir el parámetro PROCESSES (requiere reiniciar la instancia).',
    trampa: 'El número de procesos por sí solo no dice nada: 180 procesos es sano con un tope de 1000 y crítico con un tope de 200. Por eso el monitor mide el porcentaje, no la cantidad.',
    origen: 'Calculado sobre V$RESOURCE_LIMIT (processes)',
  },
  util_sesiones_pct: {
    titulo: 'Uso de sesiones',
    que: 'Qué porcentaje del cupo de sesiones está ocupado. Una sesión es cada conexión de un usuario o aplicación a la base.',
    porQue: 'Mismo riesgo que los procesos: al llegar al tope, las conexiones nuevas son rechazadas.',
    siFalla: 'Buscar quién tiene sesiones abiertas y por qué. Suele ser un pool de conexiones mal dimensionado en la aplicación, no un problema de la base.',
    origen: 'Calculado sobre V$RESOURCE_LIMIT (sessions)',
  },
  p6_sesiones_bloqueadas: {
    titulo: 'Sesiones bloqueadas',
    que: 'Cuántas conexiones están detenidas esperando a que otra libere algo que necesitan.',
    porQue: 'Es un problema real y presente, con responsable identificable: alguien dejó una transacción abierta. Para el usuario final se ve como una pantalla congelada.',
    siFalla: 'Identificar quién bloquea a quién y decidir si esperar o cortar la sesión bloqueante. Casi siempre es una transacción que alguien abrió y no cerró.',
    trampa: 'Un bloqueo de dos segundos es normal y no significa nada; uno de cinco minutos es un incidente. Por eso el monitor mide también cuánto lleva el bloqueo más largo.',
    origen: 'V$SESSION, sesiones con BLOCKING_SESSION',
  },
  bloqueo_max_seg: {
    titulo: 'Bloqueo más largo',
    que: 'Cuántos segundos lleva esperando la sesión bloqueada que más tiempo lleva atascada.',
    porQue: 'Distingue el ruido del incidente. Que haya bloqueos es normal en una base con actividad; que uno lleve minutos no lo es.',
    siFalla: 'Ir directo a esa sesión: es la que está causando el daño visible al usuario.',
    origen: 'V$SESSION, SECONDS_IN_WAIT de la sesión bloqueada',
  },
  p1_procesos_actuales: {
    titulo: 'Procesos actuales',
    que: 'Cuántos procesos tiene Oracle abiertos en este momento.',
    porQue: 'Es el numerador del uso de procesos. Solo tiene sentido leído junto a su tope.',
    siFalla: 'No se evalúa solo: mirar «Uso de procesos», que es el que compara contra el límite.',
    trampa: 'Un número alto no es malo por sí mismo. Sin conocer el tope configurado, este dato no permite concluir nada.',
    origen: 'V$RESOURCE_LIMIT (processes), CURRENT_UTILIZATION',
  },
  p2_procesos_maximos: {
    titulo: 'Pico histórico de procesos',
    que: 'El máximo de procesos simultáneos que se alcanzó desde que la instancia arrancó.',
    porQue: 'Sirve para dimensionar: si el pico se acerca al tope, conviene subirlo antes de que sea urgente.',
    siFalla: 'No genera alerta. Es información para planificar capacidad.',
    trampa: 'Solo sube, nunca baja. No sirve como señal de salud actual: un pico puntual de hace tres semanas seguiría marcando alto hoy.',
    origen: 'V$RESOURCE_LIMIT (processes), MAX_UTILIZATION',
  },
  p3_sesiones_actuales: {
    titulo: 'Sesiones actuales',
    que: 'Cuántas conexiones hay abiertas contra la base en este momento.',
    porQue: 'Es el numerador del uso de sesiones.',
    siFalla: 'No se evalúa solo: mirar «Uso de sesiones».',
    origen: 'V$RESOURCE_LIMIT (sessions), CURRENT_UTILIZATION',
  },
  p4_sesiones_activas: {
    titulo: 'Sesiones activas',
    que: 'Cuántas conexiones están ejecutando algo ahora mismo, en lugar de estar esperando.',
    porQue: 'Da una idea de la carga real de trabajo, más allá de cuánta gente está conectada.',
    siFalla: 'No puntúa. Se usa como contexto para interpretar el resto.',
    origen: "V$SESSION con STATUS='ACTIVE'",
  },
  p5_sesiones_inactivas: {
    titulo: 'Sesiones inactivas',
    que: 'Conexiones abiertas que en este momento no están haciendo nada.',
    porQue: 'Ayuda a detectar fugas de conexiones en las aplicaciones.',
    siFalla: 'No puntúa por sí sola. La señal útil es la tendencia: si crece sin parar y nunca baja, alguna aplicación abre conexiones y no las cierra.',
    trampa: 'Un número alto NO es malo. Un pool de conexiones sano tiene muchas sesiones inactivas esperando trabajo — para eso existe. Penalizarlas sería castigar el buen diseño.',
    origen: "V$SESSION con STATUS='INACTIVE'",
  },
  p7_operaciones_largas: {
    titulo: 'Operaciones prolongadas',
    que: 'Cuántas operaciones llevan corriendo el tiempo suficiente como para que Oracle reporte su progreso.',
    porQue: 'Explica por qué la base puede sentirse lenta en un momento dado.',
    siFalla: 'No puntúa. Sirve para interpretar el resto: una carga alta con un backup corriendo es esperable.',
    trampa: 'Casi siempre son operaciones legítimas: crear un índice o correr un respaldo aparecen aquí y no son un problema.',
    origen: 'V$SESSION_LONGOPS',
  },
  p8_peor_util_recurso: {
    titulo: 'Recurso más exigido',
    que: 'De todos los recursos con cupo que Oracle controla, el porcentaje del que está más cerca de su límite.',
    porQue: 'Detecta topes que nadie está mirando, más allá de procesos y sesiones.',
    siFalla: 'No puntúa todavía. Sirve como señal temprana de que algún límite se está acercando.',
    origen: 'V$RESOURCE_LIMIT, peor relación uso/límite',
  },

  // ------------------------------------------------------- procesos de fondo
  b1_procesos_caidos: {
    titulo: 'Procesos internos caídos',
    que: 'Cuántos de los cinco procesos internos imprescindibles de Oracle no están funcionando. Son los que escriben a disco, registran los cambios y limpian.',
    porQue: 'No son opcionales: sin ellos la base no puede operar correctamente. Es de los pocos casos donde no hay grados — o están los cinco, o hay un problema serio.',
    siFalla: 'Revisar el log de alertas de Oracle de inmediato. Un proceso interno caído suele venir acompañado de un error más grave.',
    origen: 'V$BGPROCESS (DBW0, LGWR, CKPT, PMON, SMON)',
  },
  b2_lgwr_espera_avg: {
    titulo: 'Espera al confirmar cambios',
    que: 'Cuánto tarda en promedio Oracle en dejar constancia en disco de que una transacción se confirmó.',
    porQue: 'Toda confirmación espera a que esto termine. Si se pone lento, cada operación de escritura de cada usuario se siente lenta.',
    siFalla: 'Casi siempre es el disco donde viven los redo logs. Revisar su rendimiento, o moverlos a un disco más rápido.',
    origen: 'V$SYSTEM_EVENT, evento log file sync',
  },
  b3_dbwr_espera_avg: {
    titulo: 'Espera al escribir a disco',
    que: 'Cuánto tarda en promedio el proceso que baja a disco los datos modificados en memoria.',
    porQue: 'Si se atrasa, la memoria se llena de datos pendientes de escribir y la base termina frenándose para esperarlo.',
    siFalla: 'Revisar el rendimiento del disco de datos. También puede indicar que hay mucha más escritura de la que el almacenamiento aguanta.',
    origen: 'V$SYSTEM_EVENT, evento de escritura de DBWR',
  },
  b4_ckpt_switch_incompleto: {
    titulo: 'Cambios de log forzados',
    que: 'Cuántas veces Oracle tuvo que esperar porque necesitaba reutilizar un redo log que todavía no terminaba de procesarse.',
    porQue: 'Cada una de esas veces la base se detiene brevemente. Es una pausa invisible que se acumula.',
    siFalla: 'Casi siempre significa que los redo logs son demasiado pequeños para el volumen de cambios. La solución es agrandarlos o agregar más grupos.',
    origen: 'V$SYSTEM_EVENT, log file switch (checkpoint incomplete)',
  },

  // ----------------------------------------------------------------- memoria
  pga_uso_pct: {
    titulo: 'Uso de memoria de trabajo (PGA)',
    que: 'Cuánta memoria están usando las operaciones (ordenar, agrupar, cruzar tablas) comparada con la que Oracle tiene reservada para eso.',
    porQue: 'Cuando una operación no cabe en memoria, Oracle la resuelve usando disco temporal, que es mucho más lento. Ahí es donde el usuario nota la degradación.',
    siFalla: 'Revisar si hay consultas pesadas ordenando mucho más de lo necesario. Si la carga es legítima, hay que subir PGA_AGGREGATE_TARGET.',
    trampa: 'Puede pasar del 100 % sin que sea un error. El valor configurado es un objetivo, no un tope duro: Oracle puede excederlo, y hacerlo ocasionalmente es normal.',
    origen: 'Calculado sobre V$PGASTAT',
  },
  m8_over_alloc_delta: {
    titulo: 'Excesos de memoria en este intervalo',
    que: 'Cuántas veces, desde la última medición, Oracle necesitó más memoria de trabajo de la que tenía reservada.',
    porQue: 'Es la señal directa de presión de memoria: no una estimación, sino un conteo de veces que la memoria no alcanzó.',
    siFalla: 'Si ocurre de forma sostenida, la memoria reservada para operaciones se quedó corta para la carga actual.',
    trampa: 'Oracle publica este contador acumulado desde que arrancó, así que solo crece. El monitor mide la diferencia entre mediciones — si usara el acumulado, una instancia con meses encendida se vería siempre mal.',
    origen: 'V$PGASTAT, over allocation count (diferencia entre muestras)',
  },
  m10_multipass_delta: {
    titulo: 'Operaciones que usaron disco',
    que: 'Cuántas operaciones, desde la última medición, no cupieron en memoria y tuvieron que resolverse haciendo varias pasadas sobre disco.',
    porQue: 'Es la consecuencia concreta de la falta de memoria, medida en operaciones reales afectadas, no en porcentajes.',
    siFalla: 'Las consultas afectadas están corriendo mucho más lento de lo que podrían. Buscar las consultas que más ordenan.',
    origen: 'V$SQL_WORKAREA_HISTOGRAM (diferencia entre muestras)',
  },
  m1_sga_total_bytes: {
    titulo: 'Memoria compartida total (SGA)',
    que: 'Cuánta memoria tiene reservada Oracle para compartir entre todas las conexiones: caché de datos, planes de consulta, diccionario.',
    porQue: 'Es el tamaño de la caché de la base. Contexto para interpretar el resto.',
    siFalla: 'No se evalúa. Es un dato de configuración, no de salud.',
    trampa: 'Que esté llena es lo normal y lo deseable. Es una caché, y una caché vacía es memoria desperdiciada. Aquí no aplica «más uso = peor».',
    origen: 'V$SGAINFO, Maximum SGA Size',
  },
  m2_sga_libre_bytes: {
    titulo: 'Memoria compartida sin asignar',
    que: 'Cuánta memoria de la SGA todavía no se repartió entre sus componentes.',
    porQue: 'Contexto. Un valor bajo no es un problema: significa que Oracle ya distribuyó lo que tenía.',
    siFalla: 'No se evalúa.',
    origen: 'V$SGAINFO, Free SGA Memory Available',
  },
  m3_shared_pool_bytes: {
    titulo: 'Caché de planes de consulta',
    que: 'Memoria donde Oracle guarda los planes de ejecución ya calculados y el diccionario de datos.',
    porQue: 'Contexto. Reutilizar planes evita recalcularlos en cada consulta.',
    siFalla: 'No se evalúa.',
    origen: 'V$SGASTAT, shared pool',
  },
  m4_buffer_cache_bytes: {
    titulo: 'Caché de datos',
    que: 'Memoria donde Oracle guarda los bloques de datos que ya leyó del disco.',
    porQue: 'Contexto. Es lo que evita ir a disco una y otra vez por lo mismo.',
    siFalla: 'No se evalúa.',
    origen: 'V$SGAINFO, Buffer Cache Size',
  },
  m5_pga_asignada_bytes: {
    titulo: 'Memoria de trabajo asignada',
    que: 'Cuánta memoria de trabajo tiene repartida Oracle entre las conexiones en este momento.',
    porQue: 'Es el numerador del uso de memoria de trabajo.',
    siFalla: 'No se evalúa solo: mirar «Uso de memoria de trabajo».',
    origen: 'V$PGASTAT, total PGA allocated',
  },
  m6_pga_en_uso_bytes: {
    titulo: 'Memoria de trabajo en uso',
    que: 'De la memoria de trabajo asignada, cuánta se está usando de verdad ahora.',
    porQue: 'Contexto. La diferencia con la asignada es memoria reservada pero ociosa.',
    siFalla: 'No se evalúa.',
    origen: 'V$PGASTAT, total PGA inuse',
  },
  m7_pga_maxima_bytes: {
    titulo: 'Pico de memoria de trabajo',
    que: 'El máximo de memoria de trabajo que Oracle llegó a usar desde que arrancó.',
    porQue: 'Sirve para dimensionar cuánta memoria hace falta de verdad.',
    siFalla: 'No genera alerta. Es información para planificar.',
    trampa: 'Solo sube. No refleja la situación actual.',
    origen: 'V$PGASTAT, maximum PGA allocated',
  },
  m9_cache_hit_pct: {
    titulo: 'Eficiencia histórica de memoria',
    que: 'Qué porcentaje de las operaciones cupo en memoria, promediado desde que la instancia arrancó.',
    porQue: 'Se muestra como referencia, pero el monitor NO lo usa para puntuar.',
    siFalla: 'No se evalúa a propósito.',
    trampa: 'Es un promedio desde el arranque, así que después de unos días queda casi inmóvil: un episodio grave hoy lo movería décimas y el monitor no vería nada. Por eso se sustituyó por el conteo de operaciones que usaron disco.',
    origen: 'V$PGASTAT, cache hit percentage',
  },

  // ---------------------------------------------------------------- archivos
  peor_tablespace_pct: {
    titulo: 'Espacio del tablespace más lleno',
    que: 'De todos los espacios de almacenamiento de la base, qué tan lleno está el que peor va. Un tablespace es donde viven las tablas.',
    porQue: 'Es probablemente la variable más importante del monitor. Un tablespace lleno detiene la base: las escrituras empiezan a fallar. Y es de los pocos fallos que se pueden prevenir con horas de anticipación.',
    siFalla: 'Agregar espacio al tablespace, o liberar espacio borrando datos que ya no se necesitan.',
    trampa: 'El monitor usa el PEOR tablespace, nunca el promedio. Doce tablespaces al 40 % y uno al 99 % promedian 45 % y suenan tranquilos, mientras ese uno está a punto de detener la base.',
    origen: 'DBA_TABLESPACE_USAGE_METRICS (solo tablespaces permanentes)',
  },
  a2_datafiles_offline: {
    titulo: 'Archivos de datos fuera de línea',
    que: 'Cuántos archivos donde viven los datos no están disponibles.',
    porQue: 'Los datos que estaban en ese archivo no se pueden leer ni escribir. Es un fallo grave e inmediato, sin grados intermedios.',
    siFalla: 'Revisar el log de alertas de Oracle. Suele ser un disco que falló o un archivo que se movió o borró.',
    origen: "V$DATAFILE con STATUS='OFFLINE'",
  },
  a7_archivos_invalidos: {
    titulo: 'Copias de bitácora inaccesibles',
    que: 'Cuántas copias del registro de cambios de Oracle no se pueden leer.',
    porQue: 'Ese registro es lo que permite recuperar la base tras una caída. Una copia inaccesible reduce la capacidad de recuperación.',
    siFalla: 'Revisar el disco donde vive esa copia. Puede hacer falta recrear el miembro del grupo.',
    origen: "V$LOGFILE con STATUS='INVALID'",
  },
  a8_archivos_recover: {
    titulo: 'Archivos que necesitan recuperación',
    que: 'Cuántos archivos de datos quedaron en un estado del que Oracle no puede salir solo.',
    porQue: 'Esos datos están inaccesibles hasta que un administrador ejecute una recuperación. No se arregla esperando.',
    siFalla: 'Requiere intervención manual: aplicar la recuperación con RECOVER DATAFILE.',
    origen: "V$DATAFILE con STATUS='RECOVER'",
  },
  redundancia_redo: {
    titulo: 'Copias de la bitácora de cambios',
    que: 'Cuántas copias tiene el grupo peor protegido del registro de cambios. Ese registro es el cuaderno donde Oracle apunta todo cambio antes de aplicarlo.',
    porQue: 'Con una sola copia no hay red de seguridad: si ese archivo se daña, se pierde la capacidad de recuperar la base ante una caída. No se pierde el dato de hoy, se pierde el seguro.',
    siFalla: 'Agregar un segundo miembro al grupo, en un disco distinto del primero: ALTER DATABASE ADD LOGFILE MEMBER.',
    trampa: 'Es un hallazgo silencioso: la base funciona perfectamente sin redundancia, hasta el día que hace falta y no está.',
    origen: 'V$LOGFILE, mínimo de miembros por grupo',
  },
  a1_datafiles_online: {
    titulo: 'Archivos de datos disponibles',
    que: 'Cuántos archivos de datos están funcionando normalmente.',
    porQue: 'Contexto: da la escala contra la que leer los que están fuera de línea.',
    siFalla: 'No se evalúa.',
    origen: 'V$DATAFILE con estado normal',
  },
  a3_datafiles_bytes: {
    titulo: 'Tamaño total de los datos',
    que: 'Cuánto ocupan en disco todos los archivos de datos juntos.',
    porQue: 'Contexto para dimensionar el crecimiento de la base.',
    siFalla: 'No se evalúa.',
    origen: 'V$DATAFILE, suma de BYTES',
  },
  a4_peor_tablespace_pct: {
    titulo: 'Espacio del tablespace más lleno (crudo)',
    que: 'El mismo dato que «Espacio del tablespace más lleno», tal como sale de Oracle.',
    porQue: 'Se conserva con su nombre original para poder auditar de dónde salió la puntuación.',
    siFalla: 'Ver la ficha de «Espacio del tablespace más lleno».',
    origen: 'DBA_TABLESPACE_USAGE_METRICS',
  },
  a4_tablespaces_riesgo: {
    titulo: 'Tablespaces en riesgo',
    que: 'Cuántos espacios de almacenamiento superaron el umbral de atención.',
    porQue: 'Distingue «uno solo va mal» de «se están llenando todos», que son problemas distintos.',
    siFalla: 'No puntúa. Sirve para decidir si el problema es puntual o general.',
    origen: 'DBA_TABLESPACE_USAGE_METRICS, conteo sobre el umbral',
  },
  a5_tempfiles_online: {
    titulo: 'Archivos temporales disponibles',
    que: 'Cuántos archivos de trabajo temporal están funcionando. Se usan cuando una operación no cabe en memoria.',
    porQue: 'Sin ellos, las consultas que necesitan ordenar mucho fallan.',
    siFalla: 'No puntúa todavía. Se recolecta como contexto.',
    origen: 'V$TEMPFILE',
  },
  a5_tempfiles_bytes: {
    titulo: 'Tamaño del espacio temporal',
    que: 'Cuánto espacio hay reservado para operaciones temporales.',
    porQue: 'Contexto.',
    siFalla: 'No se evalúa.',
    origen: 'V$TEMPFILE, suma de BYTES',
  },
  a6_grupos_redo: {
    titulo: 'Grupos de bitácora',
    que: 'En cuántos grupos está organizado el registro de cambios de Oracle.',
    porQue: 'Contexto para leer la redundancia.',
    siFalla: 'No se evalúa.',
    origen: 'V$LOG',
  },
  a6_min_miembros_grupo: {
    titulo: 'Copias de la bitácora (crudo)',
    que: 'El mismo dato que «Copias de la bitácora de cambios», tal como sale de Oracle.',
    porQue: 'Se conserva con su nombre original para poder auditar la puntuación.',
    siFalla: 'Ver la ficha de «Copias de la bitácora de cambios».',
    origen: 'V$LOGFILE',
  },
};

/** Variables acumuladas que solo existen para calcular diferencias entre muestras. */
const AUXILIAR: AyudaVariable = {
  titulo: 'Contador acumulado',
  que: 'Un contador que Oracle lleva desde que la instancia arrancó y que solo crece.',
  porQue: 'El monitor lo guarda para poder calcular cuánto cambió entre dos mediciones. La señal está en la diferencia, no en el total.',
  siFalla: 'No se evalúa directamente. La variable que sí puntúa es la diferencia entre muestras.',
  origen: 'Vistas de Oracle, valor acumulado',
};

/**
 * Devuelve la ficha de una variable, o null si no hay ninguna escrita.
 *
 * Los contadores `*_acum` comparten una ficha genérica en vez de veinte
 * casi idénticas: todos significan lo mismo (un acumulado que solo sirve para
 * sacar la diferencia). Devolver null en vez de inventar una ficha vacía es
 * deliberado -- la interfaz no debe mostrar un botón de ayuda que al abrirse
 * no explique nada.
 */
export function ayudaDeVariable(variable: string): AyudaVariable | null {
  const ficha = AYUDA_VARIABLES[variable];
  if (ficha) return ficha;
  if (variable.endsWith('_acum')) return AUXILIAR;
  return null;
}
