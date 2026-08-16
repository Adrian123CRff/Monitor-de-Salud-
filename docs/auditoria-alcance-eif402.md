# Auditoría de alcance: propuesta EIF402 contra el código real

Cruce entre `EIF402 MONITOR DE SALUD DE UNA BASE DE DATOS.odt` (la propuesta
formal, 27 secciones) más las notas de clase sueltas (`Dijo el profe que el
elijio oracle.txt`, `procesos - memoria y los archivos..txt`, `Resumen Este
conjunto de fuentes.txt`), contra el estado real del código en `main` a la
fecha de este documento.

Convención de la tabla: **implementado** (existe y puntúa/funciona tal cual
se pidió), **contexto** (se recolecta, no se puntúa todavía — a propósito),
**excede** (se construyó más de lo que pedía la propuesta), **no adoptado /
fuera de alcance** (mencionado en las fuentes, deliberadamente no
construido).

Versión interactiva (con badges de color): ver el artifact publicado en la
conversación de Claude Code de esta sesión.

---

## Monitor de procesos (§6–7 de la propuesta)

La propuesta pide p1–p8 desde `V$PROCESS / V$SESSION / V$RESOURCE_LIMIT /
V$SESSION_LONGOPS / V$WAIT_CHAINS`. El código las trae todas en una sola
consulta (`JdbcRecolectorProcesos`), pero solo puntúa un subconjunto — el
resto se persiste como contexto, decisión explícita, no descuido.

| Var. | Variable propuesta | Estado | Dónde / por qué |
|---|---|---|---|
| p1 | Procesos actuales | implementado | alimenta `util_procesos_pct` (sí puntúa) |
| p2 | Procesos máximos | contexto | `limite_procesos` |
| p3 | Sesiones actuales | implementado | alimenta `util_sesiones_pct` (sí puntúa) |
| p4 | Sesiones activas | contexto | persistido, no puntuado a propósito |
| p5 | Sesiones inactivas | contexto | "p5 alto no es malo por sí solo" — catalogo-variables.md |
| p6 | Sesiones bloqueadas | excede | puntúa **y** tiene alerta propia con confirmación 2-de-3 |
| p7 | Operaciones prolongadas | contexto | `V$SESSION_LONGOPS` se consulta; sin umbral todavía |
| p8 | Uso de recursos (peor %) | contexto | calculado, no puntuado todavía |

**Procesos de fondo — no estaba en la propuesta formal (excede).** Las notas
de clase (transcripción BD13-8) sí insisten en DBWR/LGWR/CKPT/PMON/SMON
como "los que deberían tener prioridad", pero el `.odt` formal no los
separa de p1–p8. El código los trata como un sub-índice independiente
(`IP_fondo`, ADR 0006, `b1`–`b4`) que se combina con `IP_usuarios` —
`b1_procesos_caidos` es veto absoluto **y** alerta propia (Módulo D3).

**Convención invertida en IP (desviación documentada).** La propuesta
define IP como *riesgo* (0–69 normal, 95–100 crítico — más alto es peor).
El código usa la convención opuesta a propósito: `100 = sano` en todo el
sistema (IP/IM/IA/ISBD), para no mezclar dos escalas donde a veces "más" es
bueno y a veces malo. Mismo dato, sentido invertido, decisión de diseño.

## Monitor de memoria (§8–12)

m1–m9 desde `V$SGAINFO / V$SGASTAT / V$PGASTAT`, tal como pide la
propuesta. `V$MEMORY_DYNAMIC_COMPONENTS` (mencionada como fuente opcional)
no se usa — no hizo falta.

| Var. | Variable propuesta | Estado | Dónde / por qué |
|---|---|---|---|
| m1–m4 | Tamaño/libre SGA, Shared Pool, Buffer Cache | contexto | "m1–m6 son contexto, no puntuación" — decisión explícita |
| m5–m7 | PGA asignada / en uso / máxima | excede | deriva `pga_uso_pct` = m5/target — esa sí puntúa (40% del componente) |
| m8 | Over-allocation | excede | detectado el bug del acumulado (la propuesta no lo advierte); corregido a delta del intervalo |
| m9 | Cache hit de PGA | descartado | acumulado desde el arranque, no puntuable — sustituido por m10 (multipass, contador real) |

**"Más uso de memoria ≠ peor salud" (§12), respetado.** La propuesta lo
advierte explícitamente: *"no se debe interpretar simplemente 'más memoria
utilizada = peor salud'"*. El código sigue esa regla al pie de la letra —
nada puntúa sobre el tamaño de la SGA, solo sobre presión (over-allocation,
multipass) y utilización de PGA contra su target.

## Monitor de archivos (§13–15)

a1–a8 completas, sobre `V$DATAFILE / V$TEMPFILE / V$LOG / V$LOGFILE /
DBA_TABLESPACE_USAGE_METRICS`.

| Var. | Variable propuesta | Estado | Dónde / por qué |
|---|---|---|---|
| a1 / a2 | Datafiles online / offline | excede | a2 puntúa **y** es veto absoluto **y** alerta binaria inmediata |
| a3 | Tamaño de datafiles | contexto | persistido, no puntuado |
| a4 | Espacio de tablespaces | excede | peor tablespace puntúa + veto a 98% + detalle por tablespace persistido + alerta con histéresis |
| a5 | Tempfiles | contexto | online/bytes recolectados, sin umbral |
| a6 | Redo logs | excede | mínimo de miembros por grupo puntúa como `redundancia_redo` (1 miembro = sin copia) |
| a7 / a8 | Archivos inválidos / inaccesibles | excede | ambos veto absoluto, no solo puntuación |

## Índice de Salud — ISBD (§16–20)

**Fórmula y pesos (§16–17), implementado.** `ISBD = 0.30·IP + 0.35·IM +
0.35·IA` — exacto a la propuesta, marcado en el código como *"valores de
diseño, no calibrados"* igual que en el documento (§17: "una etapa
posterior deberá determinar si estos pesos son adecuados" → Módulo B,
bloqueado por falta de datos reales).

**Escala 0–100 (§18), implementado.** 90–100 Óptimo · 75–89 Saludable ·
60–74 Advertencia · 40–59 Degradado · 0–39 Crítico — igual en
`Estado.java`, sin ajustes.

**"El índice global no debe ocultar un problema" (§20), excede.** Este es
el punto que más peso le da al diseño real. La propuesta lo plantea en
prosa — el código lo resuelve con un mecanismo formal de **veto absoluto**
(ADR 0003): si un componente cae bajo el umbral crítico, el estado final es
CRÍTICO sin importar el promedio ponderado, y el ISBD trae un arreglo
`causas[]` que explica por qué (exactamente el ejemplo del documento:
procesos 95 + memoria 91 + archivos 98 → "ÍNDICE GENERAL 94 / ESTADO REAL
CRÍTICO"). Además redistribuye el peso cuando un componente no se pudo
recolectar ese ciclo, algo que la propuesta no contempla.

## Sistema de alertas (§21)

**Campos por alerta: Fecha, Hora, Componente, Variable, Valor, Umbral,
Nivel, Descripción — excede.** Los ocho campos están en el registro
`Alerta` — pero el documento describe eventos puntuales ("🔴 CRÍTICO — PGA
con presión elevada") y el código construyó algo más exigente: cada alerta
es un **episodio** con apertura y cierre (no una fila por muestra),
**histéresis** (entrada/salida distintas, evita parpadeo) y
**confirmación temporal** ("2 de 3", "3 de 5") para variables ruidosas.
Cinco variables cubiertas: datafiles offline, tablespace crítico, sesiones
bloqueadas, presión de PGA, procesos de fondo caídos.

## Dashboard, histórico y modelo de datos (§22–24)

**Dashboard (§22), excede.** El mockup del documento muestra índice + 3
componentes + alertas. El dashboard real agrega: drill-down por componente
al dato crudo (clic en un tile), panel de tablespaces con barras y leyenda
de color, gráfico de evolución 24h, y una **pantalla de calibración** para
editar pesos/umbral de veto sin tocar código — nada de esto estaba pedido.

**Monitoreo histórico (§23), implementado.** Endpoint `/salud/historico` +
gráfico de evolución en el dashboard, tal como se pidió.

| Tabla propuesta (§24) | Estado | Nota |
|---|---|---|
| MONITOR_INSTANCIA | existe | V1 |
| MONITOR_PROCESOS | existe | V1 |
| MONITOR_MEMORIA | existe | V1 |
| MONITOR_ARCHIVOS | existe | V1 |
| MONITOR_INDICES | existe | V1, sin usar hasta esta sesión |
| MONITOR_ALERTAS | existe | V5 |
| MONITOR_PROCESOS_FONDO | agregada | no estaba en §24 — necesaria por ADR 0006 |
| MONITOR_TABLESPACE | agregada | no estaba en §24 — detalle por tablespace |

## Fuera de alcance — por diseño, no por olvido (§26)

El propio documento traza su ruta en versiones (§26). Todo lo de v2–v4
sigue sin construir, a propósito — documentado como "Módulo F, pausado" en
`docs/plan-trabajo-pendiente.md`.

- **Cadena de bloqueos** (v2) — grafo de sesión bloqueando sesión
  (`V$SESSION.BLOCKING_SESSION`). Estaba en el prototipo HTML aprobado; sin
  recolector ni endpoint todavía.
- **I/O, SQL** (v2) — no mencionado en las notas de clase con el mismo peso
  que procesos/memoria/archivos; no se tocó.
- **Multi-cliente** (notas de clase) — *"que el monitor sea capaz de
  monitorear otras bases de datos"* — pausado explícitamente, ADR 0001 fija
  una sola instancia.
- **Predicción / ML** (v4) — *"una red neuronal… machine learning"* de las
  notas — explícitamente v4, la etapa más lejana del propio roadmap del
  documento.

## Lo que quedó en las notas de clase y no llegó al código

- **Peso de memoria "60%", no adoptado.** Una nota suelta dice *"índice de
  peso de memoria le metería un 60%"* — pero el documento formal (§17) fija
  memoria en 35%, y es esa versión la que quedó en `Calibracion.inicial()`.
  El documento formal manda sobre la nota informal.
- **Auto-ajuste de prioridad, no construido.** *"que cuando una base de
  datos se esté poniendo mal se auto-ajuste de menor a mayor prioridad"* —
  la confirmación temporal y la histéresis atacan el mismo miedo (falsas
  alarmas), pero un auto-ajuste de prioridad en sí no existe. No estaba en
  el documento formal tampoco.
- **Cuestionario de control interno / COBIT / ISO / CISA, fuera del
  código.** Las transcripciones de audio dedican bastante tiempo a esto,
  pero es un entregable de evaluación de riesgos aparte (un
  instrumento/formulario que se aplica a una organización), no algo que
  construir dentro del software del monitor. No es una brecha de este
  repositorio.

---

Extraído de `EIF402 MONITOR DE SALUD DE UNA BASE DE DATOS.odt` (contenido
XML, 27 secciones) y las tres notas `.txt` sueltas en la raíz del proyecto,
cruzado contra `UmbralesIniciales.java`, `AlertasIniciales.java`, los tres
recolectores JDBC, y el esquema de migraciones Flyway. Detalle módulo por
módulo en `docs/plan-trabajo-pendiente.md` y `docs/adr/`.
