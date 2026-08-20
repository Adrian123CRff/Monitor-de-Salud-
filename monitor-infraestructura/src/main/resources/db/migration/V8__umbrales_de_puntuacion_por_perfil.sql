-- Los umbrales de puntuación dejan de vivir en código (UmbralesIniciales) y
-- pasan a ser datos editables sin recompilar. Desbloquea el Módulo B del plan
-- de trabajo: cuando la línea base (B1) produzca percentiles reales, aplicar
-- la calibración es un UPDATE, no un release. Ver ADR 0007.
--
-- Por qué una tabla nueva y no la monitor_umbral de V1:
--
-- 1. El esquema de V1 se escribió antes de que el dominio separara los dos
--    conceptos de umbral. Mezclaba en una sola fila la normalización a 0-100
--    (calibracion.Umbral: tipo, valor_ok, valor_critico, puntos_por_evento,
--    veto) con la clasificación en niveles de severidad para el ciclo de vida
--    de alertas (alertas.UmbralAlerta: advertencia/alto/critico + histéresis +
--    confirmación). Esta tabla cubre SOLO la primera; los umbrales de alerta
--    (AlertasIniciales) siguen en código y necesitan su propia tabla -- ver
--    el plan de trabajo, Módulo B4.
--
-- 2. V1 ataba los umbrales a calibracion_id por FK. Eso rompe al recalibrar:
--    JdbcRepositorioCalibracion.registrar() cierra la calibración vigente e
--    inserta una fila nueva cada vez que cambian los PESOS, así que los
--    umbrales quedarían huérfanos y el sistema volvería en silencio a los
--    valores de código. Pesos y umbrales son ejes de calibración
--    independientes: cambiar el peso de memoria no invalida el umbral de
--    pga_uso_pct.
--
-- monitor_umbral nunca se leyó ni se escribió (ver el comentario de V4 y
-- JdbcRepositorioCalibracion), así que no hay datos que migrar.
DROP TABLE IF EXISTS monitor_umbral;

-- ---------------------------------------------------------------
-- Paramétrico: qué perfil de umbrales le toca a cada instancia
-- ---------------------------------------------------------------
-- Requisito de las notas de clase ("una base de datos pequeña no tiene los
-- mismos umbrales que una grande"). ESTANDAR por defecto para que las
-- instancias ya existentes no cambien de comportamiento.
ALTER TABLE monitor_instancia
    ADD COLUMN perfil VARCHAR(20) DEFAULT 'ESTANDAR' NOT NULL;

ALTER TABLE monitor_instancia
    ADD CONSTRAINT ck_instancia_perfil
    CHECK (perfil IN ('ESTANDAR', 'PEQUENA', 'MEDIANA', 'GRANDE'));

-- ---------------------------------------------------------------
-- Umbrales de puntuación (calibracion.Umbral), por perfil
-- ---------------------------------------------------------------
-- Las columnas son 1:1 con el record Umbral. valor_ok/valor_critico solo
-- aplican a los tipos LINEAL_*; puntos_por_evento solo a
-- PENALIZACION_DISCRETA; ambos quedan en 0 para los tipos que no los usan
-- (igual que hacen las factory de Umbral).
CREATE TABLE monitor_umbral_puntuacion (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    perfil               VARCHAR(20)  NOT NULL,
    grupo                VARCHAR(30)  NOT NULL,
    variable             VARCHAR(60)  NOT NULL,
    tipo                 VARCHAR(30)  NOT NULL,
    valor_ok             NUMERIC      DEFAULT 0 NOT NULL,
    valor_critico        NUMERIC      DEFAULT 0 NOT NULL,
    puntos_por_evento    NUMERIC      DEFAULT 0 NOT NULL,
    peso_en_componente   NUMERIC(4,3) NOT NULL,
    veto_absoluto        BOOLEAN      DEFAULT FALSE NOT NULL,
    veto_si_valor_supera NUMERIC,
    -- De dónde salió este número: valor de diseño, percentil observado,
    -- límite duro de Oracle o prueba de estrés. Es la trazabilidad que pide
    -- el Módulo B4 del plan ("documentando la fuente de cada uno").
    fuente               VARCHAR(300),
    actualizado_en       TIMESTAMPTZ  DEFAULT now() NOT NULL,
    CONSTRAINT uq_umbral_punt UNIQUE (perfil, grupo, variable),
    CONSTRAINT ck_umbral_punt_perfil CHECK (perfil IN ('ESTANDAR', 'PEQUENA', 'MEDIANA', 'GRANDE')),
    CONSTRAINT ck_umbral_punt_grupo  CHECK (grupo IN (
        'PROCESOS_USUARIOS', 'PROCESOS_FONDO', 'MEMORIA', 'ARCHIVOS')),
    CONSTRAINT ck_umbral_punt_tipo   CHECK (tipo IN (
        'LINEAL_INVERTIDA', 'LINEAL_DIRECTA', 'PENALIZACION_DISCRETA',
        'CRITICO_SI_HAY_ALGUNO', 'CONTEXTO')),
    -- Un tipo lineal invertido exige critico > ok, y uno directo ok > critico
    -- (Normalizador revienta con IllegalArgumentException si no) -- mejor
    -- atraparlo aquí, cuando alguien escribe el UPDATE, que en el ciclo de
    -- muestreo tres horas después.
    CONSTRAINT ck_umbral_punt_lineal CHECK (
        (tipo = 'LINEAL_INVERTIDA' AND valor_critico > valor_ok)
        OR (tipo = 'LINEAL_DIRECTA' AND valor_ok > valor_critico)
        OR tipo NOT IN ('LINEAL_INVERTIDA', 'LINEAL_DIRECTA'))
);

CREATE INDEX ix_umbral_punt_perfil ON monitor_umbral_puntuacion (perfil, grupo);

-- ---------------------------------------------------------------
-- Semilla: el perfil ESTANDAR = UmbralesIniciales, valor por valor
-- ---------------------------------------------------------------
-- Copia exacta de UmbralesIniciales.java. Los otros tres perfiles quedan
-- VACÍOS a propósito: inventar umbrales para "base pequeña" sin haber medido
-- ninguna sería justo lo que este proyecto evita en todos lados. El respaldo
-- por variable (ver RepositorioUmbrales) hace que una instancia PEQUENA
-- funcione igual que una ESTANDAR hasta que B3 produzca números defendibles,
-- y entonces se redefinen solo las variables que de verdad cambian con el
-- tamaño.
INSERT INTO monitor_umbral_puntuacion
    (perfil, grupo, variable, tipo, valor_ok, valor_critico, puntos_por_evento,
     peso_en_componente, veto_absoluto, veto_si_valor_supera, fuente)
VALUES
    ('ESTANDAR', 'PROCESOS_USUARIOS', 'util_procesos_pct', 'LINEAL_INVERTIDA',
     70, 95, 0, 0.250, FALSE, NULL,
     'Valor de diseno (documento EIF402 seccion 7: 70% advertencia, 95% critico). NO calibrado.'),
    ('ESTANDAR', 'PROCESOS_USUARIOS', 'util_sesiones_pct', 'LINEAL_INVERTIDA',
     70, 95, 0, 0.250, FALSE, NULL,
     'Valor de diseno, mismo criterio que util_procesos_pct. NO calibrado.'),
    ('ESTANDAR', 'PROCESOS_USUARIOS', 'p6_sesiones_bloqueadas', 'PENALIZACION_DISCRETA',
     0, 0, 25, 0.250, FALSE, NULL,
     'Valor de diseno: 4 sesiones bloqueadas llevan el componente a 0. NO calibrado.'),
    ('ESTANDAR', 'PROCESOS_USUARIOS', 'bloqueo_max_seg', 'LINEAL_INVERTIDA',
     5, 120, 0, 0.250, FALSE, NULL,
     'Valor de diseno: un bloqueo de 2 min ya es critico. NO calibrado.'),

    ('ESTANDAR', 'PROCESOS_FONDO', 'b1_procesos_caidos', 'CRITICO_SI_HAY_ALGUNO',
     0, 0, 0, 0.400, TRUE, NULL,
     'Limite duro: un proceso mandatorio caido no admite grado intermedio (ADR 0006).'),
    ('ESTANDAR', 'PROCESOS_FONDO', 'b2_lgwr_espera_avg', 'LINEAL_INVERTIDA',
     1, 10, 0, 0.250, FALSE, NULL,
     'Valor de diseno en centesimas de segundo, verificado contra una instancia viva. NO calibrado.'),
    ('ESTANDAR', 'PROCESOS_FONDO', 'b3_dbwr_espera_avg', 'LINEAL_INVERTIDA',
     1, 10, 0, 0.200, FALSE, NULL,
     'Valor de diseno en centesimas de segundo, verificado contra una instancia viva. NO calibrado.'),
    ('ESTANDAR', 'PROCESOS_FONDO', 'b4_ckpt_switch_incompleto', 'PENALIZACION_DISCRETA',
     0, 0, 20, 0.150, FALSE, NULL,
     'Valor de diseno sobre la delta del intervalo, nunca el acumulado. NO calibrado.'),

    ('ESTANDAR', 'MEMORIA', 'pga_uso_pct', 'LINEAL_INVERTIDA',
     90, 130, 0, 0.400, FALSE, NULL,
     'Valor de diseno: PGA sobre su target. Puede pasar de 100% legitimamente, por eso critico en 130. NO calibrado.'),
    ('ESTANDAR', 'MEMORIA', 'm8_over_alloc_delta', 'PENALIZACION_DISCRETA',
     0, 0, 20, 0.300, FALSE, NULL,
     'Valor de diseno sobre la delta del intervalo (over allocation count de PGA es acumulado). NO calibrado.'),
    ('ESTANDAR', 'MEMORIA', 'm10_multipass_delta', 'PENALIZACION_DISCRETA',
     0, 0, 10, 0.300, FALSE, NULL,
     'Valor de diseno sobre la delta del histograma de work areas (sustituye a m9, acumulado). NO calibrado.'),

    ('ESTANDAR', 'ARCHIVOS', 'peor_tablespace_pct', 'LINEAL_INVERTIDA',
     75, 95, 0, 0.400, FALSE, 98,
     'Valor de diseno + limite duro de veto en 98% (detencion inminente). NO calibrado.'),
    ('ESTANDAR', 'ARCHIVOS', 'a2_datafiles_offline', 'CRITICO_SI_HAY_ALGUNO',
     0, 0, 0, 0.200, TRUE, NULL,
     'Limite duro: un datafile OFFLINE no admite grado intermedio.'),
    ('ESTANDAR', 'ARCHIVOS', 'a7_archivos_invalidos', 'CRITICO_SI_HAY_ALGUNO',
     0, 0, 0, 0.200, TRUE, NULL,
     'Limite duro: un miembro de redo INVALID no admite grado intermedio.'),
    ('ESTANDAR', 'ARCHIVOS', 'a8_archivos_recover', 'CRITICO_SI_HAY_ALGUNO',
     0, 0, 0, 0.100, TRUE, NULL,
     'Limite duro: un datafile en RECOVER no admite grado intermedio.'),
    ('ESTANDAR', 'ARCHIVOS', 'redundancia_redo', 'LINEAL_DIRECTA',
     2, 1, 0, 0.100, FALSE, NULL,
     'Limite duro invertido: 2 miembros por grupo es lo sano, 1 significa sin copia.');
