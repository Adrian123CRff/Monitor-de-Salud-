-- Esquema histórico del monitor (ADR 0002: PostgreSQL, separado de la
-- instancia Oracle observada). Traducido desde el DDL de referencia en
-- monitor-salud-oracle/references/modelo-datos.md:
--   NUMBER            -> NUMERIC / BIGINT (según uso)
--   VARCHAR2(n)       -> VARCHAR(n)
--   TIMESTAMP         -> TIMESTAMPTZ
--   NUMBER(1) CHECK(0,1) -> BOOLEAN
--   GENERATED ALWAYS AS IDENTITY se mantiene igual (soportado en Postgres 10+).

-- ---------------------------------------------------------------
-- Catálogo: qué instancias se monitorean
-- ---------------------------------------------------------------
CREATE TABLE monitor_instancia (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias       VARCHAR(60)  NOT NULL,
    host        VARCHAR(255) NOT NULL,
    puerto      INTEGER      NOT NULL,
    servicio    VARCHAR(120) NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,   -- CDB | PDB | NON_CDB
    con_name    VARCHAR(128),            -- nombre del contenedor si aplica
    version_bd  VARCHAR(40),
    activa      BOOLEAN DEFAULT TRUE NOT NULL,
    creada_en   TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT uq_instancia_alias UNIQUE (alias),
    CONSTRAINT ck_instancia_tipo  CHECK (tipo IN ('CDB', 'PDB', 'NON_CDB'))
);

-- ---------------------------------------------------------------
-- Configuración: umbrales y pesos versionados
-- ---------------------------------------------------------------
CREATE TABLE monitor_calibracion (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(60)  NOT NULL,
    vigente_desde   TIMESTAMPTZ  NOT NULL,
    vigente_hasta   TIMESTAMPTZ,
    peso_procesos   NUMERIC(4,3) NOT NULL,
    peso_memoria    NUMERIC(4,3) NOT NULL,
    peso_archivos   NUMERIC(4,3) NOT NULL,
    justificacion   VARCHAR(1000),
    CONSTRAINT ck_pesos_suman CHECK (
        ABS(peso_procesos + peso_memoria + peso_archivos - 1) < 0.001)
);

CREATE TABLE monitor_umbral (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    calibracion_id        BIGINT       NOT NULL,
    variable              VARCHAR(60)  NOT NULL,
    peso_en_componente    NUMERIC(4,3) NOT NULL,
    valor_ok              NUMERIC      NOT NULL,
    valor_advertencia     NUMERIC      NOT NULL,
    valor_alto            NUMERIC      NOT NULL,
    valor_critico          NUMERIC      NOT NULL,
    histeresis            NUMERIC      DEFAULT 0 NOT NULL,
    muestras_confirmacion INTEGER      DEFAULT 1 NOT NULL,
    invertir              BOOLEAN      DEFAULT TRUE NOT NULL,
    fuente                VARCHAR(200),
    CONSTRAINT fk_umbral_cal FOREIGN KEY (calibracion_id) REFERENCES monitor_calibracion(id),
    CONSTRAINT uq_umbral UNIQUE (calibracion_id, variable)
);

-- ---------------------------------------------------------------
-- Muestras crudas: procesos
-- ---------------------------------------------------------------
CREATE TABLE monitor_procesos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id           BIGINT      NOT NULL,
    muestreado_en          TIMESTAMPTZ NOT NULL,
    p1_procesos_actuales   NUMERIC,
    p2_procesos_maximos    NUMERIC,
    p3_sesiones_actuales   NUMERIC,
    p4_sesiones_activas    NUMERIC,
    p5_sesiones_inactivas  NUMERIC,
    p6_sesiones_bloqueadas NUMERIC,
    p7_operaciones_largas  NUMERIC,
    p8_peor_util_recurso   NUMERIC(6,2),
    limite_procesos        NUMERIC,
    limite_sesiones        NUMERIC,
    bloqueo_max_seg        NUMERIC,
    CONSTRAINT fk_proc_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Muestras crudas: memoria
-- ---------------------------------------------------------------
CREATE TABLE monitor_memoria (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id          BIGINT      NOT NULL,
    muestreado_en         TIMESTAMPTZ NOT NULL,
    m1_sga_total_bytes    NUMERIC,
    m2_sga_libre_bytes    NUMERIC,
    m3_shared_pool_bytes  NUMERIC,
    m4_buffer_cache_bytes NUMERIC,
    m5_pga_asignada_bytes NUMERIC,
    m6_pga_en_uso_bytes   NUMERIC,
    m7_pga_maxima_bytes   NUMERIC,
    m8_over_alloc_acum    NUMERIC,      -- ACUMULADO desde el arranque
    m9_cache_hit_pct      NUMERIC(6,2), -- ACUMULADO desde el arranque
    pga_target_bytes      NUMERIC,
    m8_over_alloc_delta   NUMERIC,      -- derivada, NULL si hubo reinicio
    instancia_reiniciada  BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_mem_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Muestras crudas: archivos (agregados de instancia)
-- ---------------------------------------------------------------
CREATE TABLE monitor_archivos (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id          BIGINT      NOT NULL,
    muestreado_en         TIMESTAMPTZ NOT NULL,
    a1_datafiles_online   NUMERIC,
    a2_datafiles_offline  NUMERIC,
    a3_datafiles_bytes    NUMERIC,
    a4_peor_tablespace_pct NUMERIC(6,2),
    a4_tablespaces_riesgo NUMERIC,
    a5_tempfiles_online   NUMERIC,
    a5_tempfiles_bytes    NUMERIC,
    a6_grupos_redo        NUMERIC,
    a6_min_miembros_grupo NUMERIC,
    a7_archivos_invalidos NUMERIC,
    a8_archivos_recover   NUMERIC,
    CONSTRAINT fk_arch_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Detalle por tablespace: el agregado no basta para el dashboard
-- ---------------------------------------------------------------
CREATE TABLE monitor_tablespace (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id    BIGINT       NOT NULL,
    muestreado_en   TIMESTAMPTZ  NOT NULL,
    tablespace_name VARCHAR(128) NOT NULL,
    used_percent    NUMERIC(6,2) NOT NULL,
    used_bytes      NUMERIC,
    max_bytes       NUMERIC,
    CONSTRAINT fk_ts_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

-- ---------------------------------------------------------------
-- Índices calculados
-- ---------------------------------------------------------------
CREATE TABLE monitor_indices (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id    BIGINT      NOT NULL,
    calculado_en    TIMESTAMPTZ NOT NULL,
    ip              NUMERIC(6,2) NOT NULL,   -- 0-100, salud
    im              NUMERIC(6,2) NOT NULL,
    ia              NUMERIC(6,2) NOT NULL,
    isbd            NUMERIC(6,2) NOT NULL,
    estado          VARCHAR(20) NOT NULL,    -- OPTIMO|SALUDABLE|ADVERTENCIA|DEGRADADO|CRITICO
    estado_por_veto BOOLEAN DEFAULT FALSE NOT NULL,
    calibracion_id  BIGINT      NOT NULL,
    CONSTRAINT fk_idx_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id),
    CONSTRAINT fk_idx_cal  FOREIGN KEY (calibracion_id) REFERENCES monitor_calibracion(id),
    CONSTRAINT ck_idx_estado CHECK (estado IN
        ('OPTIMO', 'SALUDABLE', 'ADVERTENCIA', 'DEGRADADO', 'CRITICO')),
    CONSTRAINT ck_idx_rango CHECK (
        ip BETWEEN 0 AND 100 AND im BETWEEN 0 AND 100 AND
        ia BETWEEN 0 AND 100 AND isbd BETWEEN 0 AND 100)
);

-- ---------------------------------------------------------------
-- Alertas (episodios con duración, no eventos puntuales)
-- ---------------------------------------------------------------
CREATE TABLE monitor_alertas (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id BIGINT      NOT NULL,
    abierta_en   TIMESTAMPTZ NOT NULL,
    cerrada_en   TIMESTAMPTZ,
    componente   VARCHAR(20)  NOT NULL,  -- PROCESOS|MEMORIA|ARCHIVOS
    variable     VARCHAR(60)  NOT NULL,
    entidad      VARCHAR(128),           -- 'USERS' para un tablespace concreto
    valor        NUMERIC,
    umbral       NUMERIC,
    nivel        VARCHAR(20)  NOT NULL,  -- ADVERTENCIA|ALTO|CRITICO
    descripcion  VARCHAR(500) NOT NULL,
    CONSTRAINT fk_alerta_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id),
    CONSTRAINT ck_alerta_comp  CHECK (componente IN ('PROCESOS', 'MEMORIA', 'ARCHIVOS')),
    CONSTRAINT ck_alerta_nivel CHECK (nivel IN ('ADVERTENCIA', 'ALTO', 'CRITICO'))
);

-- ---------------------------------------------------------------
-- Índices de acceso: todas las consultas del dashboard son
-- "lo más reciente de esta instancia" o un rango [desde,hasta].
-- ---------------------------------------------------------------
CREATE INDEX ix_proc_inst_ts   ON monitor_procesos  (instancia_id, muestreado_en DESC);
CREATE INDEX ix_mem_inst_ts    ON monitor_memoria   (instancia_id, muestreado_en DESC);
CREATE INDEX ix_arch_inst_ts   ON monitor_archivos  (instancia_id, muestreado_en DESC);
CREATE INDEX ix_ts_inst_ts     ON monitor_tablespace(instancia_id, muestreado_en DESC);
CREATE INDEX ix_idx_inst_ts    ON monitor_indices   (instancia_id, calculado_en DESC);
CREATE INDEX ix_alerta_abierta ON monitor_alertas   (instancia_id, cerrada_en, abierta_en DESC);
