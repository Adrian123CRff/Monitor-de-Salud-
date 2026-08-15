-- El ISBD nunca se persistía (monitor_indices existe desde V1, sin usar).
-- Ahora que un componente puede faltar por fallo de recolección (Isbd.parcial,
-- ver MuestrearInstanciaServicio.recolectarSeguro), ip/im/ia ya no pueden ser
-- NOT NULL -- CHECK ck_idx_rango sigue siendo válido tal cual: en Postgres
-- "NULL BETWEEN x AND y" evalúa a NULL, no a FALSE, así que un CHECK no
-- rechaza una fila con NULL.
--
-- calibracion_id también se relaja: Calibracion (dominio) no lleva su propio
-- id todavía (es solo pesos + veto, ver JdbcRepositorioCalibracion), así que
-- no hay forma de saber qué fila de monitor_calibracion produjo cada ISBD.
-- Documentado, no oculto -- JdbcRepositorioIndices no llena esa columna.

ALTER TABLE monitor_indices ALTER COLUMN ip DROP NOT NULL;
ALTER TABLE monitor_indices ALTER COLUMN im DROP NOT NULL;
ALTER TABLE monitor_indices ALTER COLUMN ia DROP NOT NULL;
ALTER TABLE monitor_indices ALTER COLUMN calibracion_id DROP NOT NULL;
ALTER TABLE monitor_indices ADD COLUMN parcial BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE monitor_indices ADD COLUMN causas VARCHAR(2000);
