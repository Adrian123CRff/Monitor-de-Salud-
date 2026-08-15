-- Soporta el cálculo real de deltas de memoria (ver CalculadorDelta):
-- m8_over_alloc_delta ya existía en el diseño original (V1) pero nunca se
-- llenaba; m10_multipass_* es nuevo -- reemplaza a cache_hit_pct como señal
-- de presión de PGA porque cache_hit_pct es un promedio acumulado (no se
-- le puede sacar delta) y multipasses_executions sí es un contador real
-- (V$SQL_WORKAREA_HISTOGRAM, verificado contra una instancia Oracle viva).

ALTER TABLE monitor_memoria ADD COLUMN m10_multipass_acum NUMERIC;
ALTER TABLE monitor_memoria ADD COLUMN m10_multipass_delta NUMERIC;
