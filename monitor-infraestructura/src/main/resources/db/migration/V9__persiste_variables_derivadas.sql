-- El histórico guardaba el crudo pero NO las variables derivadas que
-- realmente puntúan, así que una muestra leída de vuelta no se podía volver
-- a puntuar igual que cuando se tomó.
--
-- Cómo se encontró: al construir el desglose por variable del detalle de un
-- componente (ver ConsultarComponenteServicio), ARCHIVOS daba 100 leyendo
-- del histórico mientras el ISBD del mismo ciclo decía 90. Faltaban
-- `peor_tablespace_pct` y `redundancia_redo`, que CalculadorComponente
-- simplemente se salta cuando la muestra no las trae.
--
-- De las nueve variables que hoy puntúan, cinco se perdían en el viaje:
--   util_procesos_pct, util_sesiones_pct  (procesos)
--   pga_uso_pct                            (memoria)
--   peor_tablespace_pct, redundancia_redo  (archivos)
--
-- Consecuencia más grave que la pantalla: el Módulo B (calibración con
-- percentiles observados) necesita justamente la distribución histórica de
-- estas variables. No se puede sacar el percentil 95 de algo que nunca se
-- guardó.
--
-- Se persisten SOLO las tres que implican un cálculo real (una división
-- contra su límite o su target). Las dos de archivos son alias exactos de
-- columnas que ya existen -- `peor_tablespace_pct` es `a4_peor_tablespace_pct`
-- y `redundancia_redo` es `a6_min_miembros_grupo`, el mismo número con otro
-- nombre (ver el comentario de JdbcRecolectorArchivos: "aquí no hay cómputo
-- real, son el mismo valor, dos claves"). Guardar esas dos otra vez sería
-- duplicar el dato; se reexponen al leer, en JdbcRepositorioMuestras.
--
-- La regla, para que no haya que adivinarla la próxima vez: si la derivación
-- calcula algo, se persiste; si solo renombra, se reexpone al leer.

ALTER TABLE monitor_procesos ADD COLUMN util_procesos_pct NUMERIC(6,2);
ALTER TABLE monitor_procesos ADD COLUMN util_sesiones_pct NUMERIC(6,2);

-- Puede pasar de 100 legítimamente (la PGA se sale de su target sin que eso
-- sea un fallo), por eso 6,2 y no un CHECK <= 100.
ALTER TABLE monitor_memoria  ADD COLUMN pga_uso_pct       NUMERIC(6,2);

COMMENT ON COLUMN monitor_procesos.util_procesos_pct IS
    'Derivada: p1_procesos_actuales / limite_procesos * 100. Puntúa (ver UmbralesIniciales).';
COMMENT ON COLUMN monitor_procesos.util_sesiones_pct IS
    'Derivada: p3_sesiones_actuales / limite_sesiones * 100. Puntúa.';
COMMENT ON COLUMN monitor_memoria.pga_uso_pct IS
    'Derivada: m5_pga_asignada_bytes / pga_target_bytes * 100. Puntúa.';
