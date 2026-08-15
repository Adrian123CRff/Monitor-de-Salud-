-- Persistencia de procesos de fondo (ADR 0006), pendiente desde V1: no
-- existía tabla porque Componente.PROCESOS es compartido por usuarios y
-- fondo (ver RepositorioMuestrasFondo, separado de RepositorioMuestras por
-- esa ambigüedad).
--
-- b2/b3 ya NO guardan AVERAGE_WAIT (promedio acumulado desde el arranque,
-- el mismo problema que m9_cache_hit_pct en memoria): se guardan
-- TIME_WAITED/TOTAL_WAITS crudos (contadores reales de V$SYSTEM_EVENT) más
-- las columnas derivadas *_espera_avg, que MuestrearInstanciaServicio
-- calcula como delta(time_waited)/delta(total_waits) del intervalo contra
-- la última muestra guardada (ver CalculadorDelta). b4 también pasa de
-- acumulado a delta del intervalo.

CREATE TABLE monitor_procesos_fondo (
    id                              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instancia_id                    BIGINT      NOT NULL,
    muestreado_en                   TIMESTAMPTZ NOT NULL,
    b1_procesos_caidos              NUMERIC,
    b2_lgwr_time_waited_acum        NUMERIC,       -- ACUMULADO desde el arranque
    b2_lgwr_total_waits_acum        NUMERIC,       -- ACUMULADO desde el arranque
    b3_dbwr_time_waited_acum        NUMERIC,       -- ACUMULADO desde el arranque
    b3_dbwr_total_waits_acum        NUMERIC,       -- ACUMULADO desde el arranque
    b4_ckpt_switch_incompleto_acum  NUMERIC,       -- ACUMULADO desde el arranque
    b2_lgwr_espera_avg              NUMERIC(10,4), -- derivada: delta del intervalo, NULL si no hubo esperas nuevas o hubo reinicio
    b3_dbwr_espera_avg              NUMERIC(10,4), -- derivada, idem
    b4_ckpt_switch_incompleto       NUMERIC,       -- derivada: delta del intervalo, NULL si hubo reinicio
    instancia_reiniciada            BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_procfondo_inst FOREIGN KEY (instancia_id) REFERENCES monitor_instancia(id)
);

CREATE INDEX ix_procfondo_inst_ts ON monitor_procesos_fondo (instancia_id, muestreado_en DESC);
