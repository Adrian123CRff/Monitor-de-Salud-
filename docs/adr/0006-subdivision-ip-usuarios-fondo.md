# 0006 - Subdivisión de IP en IP_usuarios / IP_fondo

## Estado
Aceptado

## Contexto
`numero2.png` y la transcripción de clase (BD13-8) muestran que el
profesor pidió explícitamente que IP se subdivida en procesos de
**usuario** y procesos de **fondo** (DBWR, LGWR, CKPT, PMON, SMON), con
prioridad para estos últimos: *"Yo creo desde el punto de vista mío que a
estos son los que deberíamos de darle prioridad para determinar el índice
de procesos."* Esto quedó pendiente como P4 del registro de decisiones
desde la interrogación inicial, porque el catálogo p1-p8 del `.odt`
original no distingue entre ambos tipos de proceso.

## Decisión
IP deja de ser un único indicador plano y pasa a calcularse como la
combinación ponderada de dos sub-indicadores, cada uno con su propio
catálogo de variables:

**IP_usuarios** (V$SESSION `TYPE='USER'`, ya eran las variables cubiertas
antes de este ADR): `util_procesos_pct`, `util_sesiones_pct`,
`p6_sesiones_bloqueadas`, `bloqueo_max_seg`.

**IP_fondo** (nuevo, verificado contra una instancia Oracle 23ai Free real
antes de fijar los nombres exactos):
- `b1_procesos_caidos`: cuántos de los procesos mandatorios (DBW0, LGWR,
  CKPT, PMON, SMON — `V$BGPROCESS.PADDR = '00'`) no están activos.
  Crítico-si-hay-alguno: no hay grado intermedio, un proceso mandatorio
  caído es un problema real (ver BD13-8: *"en caso de que este proceso no
  esté funcionando bien, voy a tener un problema"*).
- `b2_lgwr_espera_avg`: espera promedio del evento `log file sync`
  (`V$SYSTEM_EVENT`) — la señal clásica de presión sobre LGWR.
- `b3_dbwr_espera_avg`: espera promedio de `db file async I/O submit` —
  señal de presión sobre DBWR.
- `b4_ckpt_switch_incompleto`: conteo de `log file switch (checkpoint
  incomplete)` — señal de checkpoints/redo logs mal dimensionados,
  asociada a CKPT.

`IP = 0.40 · IP_usuarios + 0.60 · IP_fondo` (`CombinadorSubIndicadores`,
pesos en `UmbralesIniciales.PESO_IP_USUARIOS/PESO_IP_FONDO`) — fondo pesa
más, siguiendo la prioridad que pidió el profesor, sin llevarlo al extremo
de que un solo proceso caído por sí solo decida todo el ISBD (para eso ya
está el veto de MotorIndicadores).

## Consecuencias
- (+) Refleja fielmente lo que pidió el profesor, con una fuente
  verificable (la transcripción) en vez de una interpretación libre.
- (+) `CombinadorSubIndicadores` es genérico: sirve para cualquier futura
  subdivisión de un componente (no solo procesos), sin tocar
  `MotorIndicadores`.
- (+) Los nombres de proceso (`DBW0`, no `DBWR`) y los eventos de espera
  se verificaron contra una instancia real antes de escribir el SQL, no
  se copiaron de memoria.
- (-) `b2`/`b3` son promedios acumulados desde el arranque de la
  instancia (`V$SYSTEM_EVENT.AVERAGE_WAIT`) -- mismo problema que
  `m9_cache_hit_pct`: pierden sensibilidad con el tiempo. Normalizar
  sobre la delta queda pendiente, igual que en memoria.
- (-) Los umbrales ok=1/critico=10 (centésimas de segundo, 10ms/100ms) y
  los pesos 0.40/0.60 son valores iniciales de diseño, no calibrados.
- (-) Solo cubre un DBWR (`DBW0`); instancias con varios DBWR quedan
  fuera de esta V1.
- (-) La muestra de procesos de fondo todavía no se persiste en Postgres
  (no existe una tabla para ella) -- se calcula pero no queda en el
  histórico todavía.

## Alternativas consideradas
- Mantener IP plano (p1-p8 sin subdivisión): más simple, pero ignora una
  instrucción explícita y verificable del profesor.
- Usar `V$PROCESS` en vez de `V$BGPROCESS` para detectar procesos
  caídos: `V$BGPROCESS` es la vista pensada exactamente para esto (nombre
  del proceso + si está activo), mientras que `V$PROCESS` requeriría
  filtrar por `BACKGROUND='B'` y no da el nombre corto del proceso
  (DBW0, LGWR...) de forma tan directa.
