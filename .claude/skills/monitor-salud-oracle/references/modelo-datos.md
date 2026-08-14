# Modelo de datos del monitor (esquema MONITOR_*)

DDL de referencia para el repositorio histórico. Está escrito en sintaxis Oracle
porque lo natural es que el monitor guarde su historia en Oracle, pero es
trasladable a PostgreSQL cambiando `NUMBER`→`numeric`, `VARCHAR2`→`varchar`,
`TIMESTAMP`→`timestamptz` y las secuencias por `GENERATED ALWAYS AS IDENTITY`
(que Oracle también soporta desde 12c y es lo que se usa aquí).

## Decisiones de diseño

Antes del DDL, las cuatro decisiones que lo explican. Si te piden cambiar el
esquema, revisa que la propuesta no rompa alguna de estas.

**1. Esquema separado de la instancia monitoreada.** El monitor no debe escribir
en la base que observa: contaminaría sus propias métricas (sus inserts cuentan
como sesiones y consumen espacio) y perdería el histórico justo cuando más se
necesita — cuando la instancia monitoreada cae. En un proyecto de curso basta con
un usuario distinto en otra instancia, o una segunda base ligera.

**2. Se guardan los valores crudos, no solo las puntuaciones.** Esta es la decisión
que más agradecerás. Vas a recalibrar umbrales y pesos varias veces; con los crudos
puedes recalcular todo el histórico y comparar calibraciones sobre los mismos datos.
Si guardas solo `score`, cada recalibración invalida las semanas anteriores de
muestreo y pierdes la evidencia con la que justificar la calibración en el informe.

**3. Una fila por muestra por subsistema, no una fila por variable.** Con ~25
variables cada 30 segundos, el formato largo (una fila por variable) genera millones
de filas al mes y complica cada consulta con pivots. El formato ancho es menos
elegante en teoría y mucho más práctico aquí; el catálogo de variables es fijo y
conocido.

**4. Los umbrales y pesos viven en tablas, no en el código.** Recalibrar no debe
requerir recompilar ni desplegar. Además, tener los umbrales versionados con
`vigente_desde` permite explicar por qué una muestra de marzo se evaluó distinto
que una de mayo.

---

## DDL

```sql
-- ---------------------------------------------------------------
-- Catálogo: qué instancias se monitorean
-- ---------------------------------------------------------------
CREATE TABLE monitor_instancia (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  alias           VARCHAR2(60)  NOT NULL,
  host            VARCHAR2(255) NOT NULL,
  puerto          NUMBER(5)     NOT NULL,
  servicio        VARCHAR2(120) NOT NULL,
  tipo            VARCHAR2(20)  NOT NULL,   -- CDB | PDB | NON_CDB
  con_name        VARCHAR2(128),            -- nombre del contenedor si aplica
  version_bd      VARCHAR2(40),
  activa          NUMBER(1) DEFAULT 1 NOT NULL,
  creada_en       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT uq_instancia_alias UNIQUE (alias),
  CONSTRAINT ck_instancia_tipo  CHECK (tipo IN ('CDB','PDB','NON_CDB')),
  CONSTRAINT ck_instancia_activa CHECK (activa IN (0,1))
);

-- ---------------------------------------------------------------
-- Muestras crudas: procesos
-- ---------------------------------------------------------------
CREATE TABLE monitor_procesos (
  id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id        NUMBER      NOT NULL,
  muestreado_en       TIMESTAMP   NOT NULL,
  p1_procesos_actuales    NUMBER,
  p2_procesos_maximos     NUMBER,
  p3_sesiones_actuales    NUMBER,
  p4_sesiones_activas     NUMBER,
  p5_sesiones_inactivas   NUMBER,
  p6_sesiones_bloqueadas  NUMBER,
  p7_operaciones_largas   NUMBER,
  p8_peor_util_recurso    NUMBER(6,2),
  limite_procesos     NUMBER,          -- el límite vigente en el momento de la muestra
  limite_sesiones     NUMBER,
  bloqueo_max_seg     NUMBER,          -- espera máxima de una sesión bloqueada
  CONSTRAINT fk_proc_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Muestras crudas: memoria
-- ---------------------------------------------------------------
CREATE TABLE monitor_memoria (
  id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id        NUMBER      NOT NULL,
  muestreado_en       TIMESTAMP   NOT NULL,
  m1_sga_total_bytes      NUMBER,
  m2_sga_libre_bytes      NUMBER,
  m3_shared_pool_bytes    NUMBER,
  m4_buffer_cache_bytes   NUMBER,
  m5_pga_asignada_bytes   NUMBER,
  m6_pga_en_uso_bytes     NUMBER,
  m7_pga_maxima_bytes     NUMBER,
  m8_over_alloc_acum      NUMBER,      -- ACUMULADO desde el arranque
  m9_cache_hit_pct        NUMBER(6,2), -- ACUMULADO desde el arranque
  pga_target_bytes        NUMBER,
  m8_over_alloc_delta     NUMBER,      -- derivada, NULL si hubo reinicio
  instancia_reiniciada    NUMBER(1) DEFAULT 0 NOT NULL,
  CONSTRAINT fk_mem_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id),
  CONSTRAINT ck_mem_reinicio CHECK (instancia_reiniciada IN (0,1))
);
```

> `m8_over_alloc_delta` se calcula al insertar, comparando con la muestra anterior
> de la misma instancia. Si el acumulado bajó, la instancia se reinició: marca
> `instancia_reiniciada = 1` y deja la delta en `NULL`. Registrar una delta
> negativa envenenaría el índice y las estadísticas de calibración.

```sql
-- ---------------------------------------------------------------
-- Muestras crudas: archivos (agregados de instancia)
-- ---------------------------------------------------------------
CREATE TABLE monitor_archivos (
  id                  NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id        NUMBER      NOT NULL,
  muestreado_en       TIMESTAMP   NOT NULL,
  a1_datafiles_online     NUMBER,
  a2_datafiles_offline    NUMBER,
  a3_datafiles_bytes      NUMBER,
  a4_peor_tablespace_pct  NUMBER(6,2),
  a4_tablespaces_riesgo   NUMBER,
  a5_tempfiles_online     NUMBER,
  a5_tempfiles_bytes      NUMBER,
  a6_grupos_redo          NUMBER,
  a6_min_miembros_grupo   NUMBER,
  a7_archivos_invalidos   NUMBER,
  a8_archivos_recover     NUMBER,
  CONSTRAINT fk_arch_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Detalle por tablespace: el agregado no basta para el dashboard
-- ---------------------------------------------------------------
CREATE TABLE monitor_tablespace (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id    NUMBER        NOT NULL,
  muestreado_en   TIMESTAMP     NOT NULL,
  tablespace_name VARCHAR2(128) NOT NULL,
  used_percent    NUMBER(6,2)   NOT NULL,
  used_bytes      NUMBER,
  max_bytes       NUMBER,
  CONSTRAINT fk_ts_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);
```

> Sin esta tabla el dashboard puede decir "el peor tablespace está al 93 %" pero no
> *cuál*, que es lo primero que va a preguntar quien mire la pantalla. Es la única
> excepción justificada al formato ancho, porque el número de tablespaces es
> variable y desconocido de antemano.

```sql
-- ---------------------------------------------------------------
-- Índices calculados
-- ---------------------------------------------------------------
CREATE TABLE monitor_indices (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id    NUMBER      NOT NULL,
  calculado_en    TIMESTAMP   NOT NULL,
  ip              NUMBER(6,2) NOT NULL,   -- 0-100, salud
  im              NUMBER(6,2) NOT NULL,
  ia              NUMBER(6,2) NOT NULL,
  isbd            NUMBER(6,2) NOT NULL,
  estado          VARCHAR2(20) NOT NULL,  -- OPTIMO|SALUDABLE|ADVERTENCIA|DEGRADADO|CRITICO
  estado_por_veto NUMBER(1) DEFAULT 0 NOT NULL,
  calibracion_id  NUMBER      NOT NULL,
  CONSTRAINT fk_idx_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id),
  CONSTRAINT fk_idx_cal  FOREIGN KEY (calibracion_id) REFERENCES monitor_calibracion(id),
  CONSTRAINT ck_idx_estado CHECK (estado IN
    ('OPTIMO','SALUDABLE','ADVERTENCIA','DEGRADADO','CRITICO')),
  CONSTRAINT ck_idx_rango CHECK (
    ip BETWEEN 0 AND 100 AND im BETWEEN 0 AND 100 AND
    ia BETWEEN 0 AND 100 AND isbd BETWEEN 0 AND 100)
);
```

> `estado_por_veto` marca las filas donde el estado NO se derivó de la puntuación
> sino de una regla de veto. Es lo que te permite responder en la defensa a
> "¿por qué el índice dice 78 y el estado dice CRÍTICO?" — y demostrar que la regla
> de la sección 6 funciona de verdad. El `CHECK` de rango es barato y atrapa el bug
> de polaridad si alguna vez se cuela una utilización sin invertir.

```sql
-- ---------------------------------------------------------------
-- Alertas
-- ---------------------------------------------------------------
CREATE TABLE monitor_alertas (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instancia_id    NUMBER        NOT NULL,
  abierta_en      TIMESTAMP     NOT NULL,
  cerrada_en      TIMESTAMP,
  componente      VARCHAR2(20)  NOT NULL,  -- PROCESOS|MEMORIA|ARCHIVOS
  variable        VARCHAR2(60)  NOT NULL,  -- p6_sesiones_bloqueadas, a4_peor_tablespace_pct...
  entidad         VARCHAR2(128),           -- 'USERS' para un tablespace concreto
  valor           NUMBER,
  umbral          NUMBER,
  nivel           VARCHAR2(20)  NOT NULL,  -- ADVERTENCIA|ALTO|CRITICO
  descripcion     VARCHAR2(500) NOT NULL,
  CONSTRAINT fk_alerta_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id),
  CONSTRAINT ck_alerta_comp  CHECK (componente IN ('PROCESOS','MEMORIA','ARCHIVOS')),
  CONSTRAINT ck_alerta_nivel CHECK (nivel IN ('ADVERTENCIA','ALTO','CRITICO'))
);
```

> Fíjate en `abierta_en` / `cerrada_en` en lugar de un simple `fecha_hora`. Una
> alerta es un **episodio con duración**, no un evento puntual. Con el modelo de
> evento puntual, un tablespace al 93 % durante seis horas genera 720 filas
> idénticas y el panel de alertas es ilegible. Con el modelo de episodio genera una
> fila que dice "abierta hace 6 h" — que es la información que un DBA necesita.
> Una alerta abierta con la misma `(componente, variable, entidad, nivel)` no se
> vuelve a abrir; se cierra cuando la condición desaparece de forma estable.

```sql
-- ---------------------------------------------------------------
-- Configuración: umbrales y pesos versionados
-- ---------------------------------------------------------------
CREATE TABLE monitor_calibracion (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre          VARCHAR2(60)  NOT NULL,
  vigente_desde   TIMESTAMP     NOT NULL,
  vigente_hasta   TIMESTAMP,
  peso_procesos   NUMBER(4,3)   NOT NULL,
  peso_memoria    NUMBER(4,3)   NOT NULL,
  peso_archivos   NUMBER(4,3)   NOT NULL,
  justificacion   VARCHAR2(1000),
  CONSTRAINT ck_pesos_suman CHECK (
    ABS(peso_procesos + peso_memoria + peso_archivos - 1) < 0.001)
);

CREATE TABLE monitor_umbral (
  id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  calibracion_id  NUMBER        NOT NULL,
  variable        VARCHAR2(60)  NOT NULL,
  peso_en_componente NUMBER(4,3) NOT NULL,
  valor_ok        NUMBER        NOT NULL,   -- hasta aquí, score 100
  valor_advertencia NUMBER      NOT NULL,
  valor_alto      NUMBER        NOT NULL,
  valor_critico   NUMBER        NOT NULL,   -- desde aquí, score 0
  histeresis      NUMBER        DEFAULT 0 NOT NULL,
  muestras_confirmacion NUMBER  DEFAULT 1 NOT NULL,
  invertir        NUMBER(1)     DEFAULT 1 NOT NULL,  -- 1 = más alto es peor
  fuente          VARCHAR2(200),   -- 'p95 observado 3-20 mayo' | 'límite duro Oracle' | 'criterio experto'
  CONSTRAINT fk_umbral_cal FOREIGN KEY (calibracion_id) REFERENCES monitor_calibracion(id),
  CONSTRAINT uq_umbral UNIQUE (calibracion_id, variable),
  CONSTRAINT ck_umbral_invertir CHECK (invertir IN (0,1))
);
```

> La columna `fuente` parece burocracia y es lo que salva la defensa del proyecto.
> Cuando pregunten "¿por qué 85 % y no 80 %?", la respuesta "es el percentil 95 de
> tres semanas de observación" vale mucho más que "nos pareció razonable". Anota la
> fuente en el momento de fijar el umbral; reconstruirla después es imposible.
>
> `invertir` es la materialización de la convención de polaridad: cada variable
> declara explícitamente hacia dónde va su salud, y el normalizador lo lee de la
> tabla en vez de tenerlo cableado en un `switch` gigante.

```sql
-- ---------------------------------------------------------------
-- Índices de acceso
-- ---------------------------------------------------------------
CREATE INDEX ix_proc_inst_ts   ON monitor_procesos  (instancia_id, muestreado_en DESC);
CREATE INDEX ix_mem_inst_ts    ON monitor_memoria   (instancia_id, muestreado_en DESC);
CREATE INDEX ix_arch_inst_ts   ON monitor_archivos  (instancia_id, muestreado_en DESC);
CREATE INDEX ix_ts_inst_ts     ON monitor_tablespace(instancia_id, muestreado_en DESC);
CREATE INDEX ix_idx_inst_ts    ON monitor_indices   (instancia_id, calculado_en DESC);
CREATE INDEX ix_alerta_abierta ON monitor_alertas   (instancia_id, cerrada_en, abierta_en DESC);
```

Todas las consultas del dashboard son de la forma "lo más reciente de esta
instancia" o "el rango [desde, hasta] de esta instancia". El índice compuesto
`(instancia_id, timestamp DESC)` las cubre todas. `ix_alerta_abierta` incluye
`cerrada_en` porque la consulta más frecuente del panel es "alertas abiertas"
(`cerrada_en IS NULL`).

---

## Retención

Con muestreo cada 30 s son ~2 880 filas por día y tabla; unas 86 000 al mes. Es
perfectamente manejable para un proyecto de curso y no necesitas particionado.

Si el proyecto se extiende, la estrategia estándar es agregación en cascada: mantener
el detalle 30 días, y a partir de ahí guardar solo promedios, máximos y percentiles
horarios. Menciónalo en la sección de trabajo futuro del informe — muestra que
pensaste en el crecimiento sin gastar tiempo implementándolo.
