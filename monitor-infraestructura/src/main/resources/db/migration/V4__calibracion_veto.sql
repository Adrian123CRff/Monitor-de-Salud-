-- Calibracion (dominio) = pesos de IP/IM/IA + veto (ADR 0003): pesos,
-- vetoHabilitado, umbralVetoComponente. La V1 solo cubría los pesos --
-- faltaban las columnas del veto para poder persistir el record completo.
--
-- monitor_umbral queda sin usar todavía: UmbralesIniciales (los umbrales
-- por variable) sigue siendo código, no datos -- el dominio Calibracion no
-- tiene una lista de Umbral que calibrar en caliente, solo los tres pesos
-- y el veto. Ver JdbcRepositorioCalibracion.

ALTER TABLE monitor_calibracion ADD COLUMN veto_habilitado BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE monitor_calibracion ADD COLUMN umbral_veto_componente NUMERIC(6,2) DEFAULT 40.0 NOT NULL;

-- Semilla: la calibración inicial de ADR 0003 (30/35/35, veto en 40, ver
-- Calibracion.inicial()) queda vigente desde el arranque -- así el
-- histórico no empieza vacío y vigente() siempre encuentra una fila real.
INSERT INTO monitor_calibracion
    (nombre, vigente_desde, peso_procesos, peso_memoria, peso_archivos,
     veto_habilitado, umbral_veto_componente, justificacion)
VALUES
    ('inicial-adr-0003', now(), 0.30, 0.35, 0.35, TRUE, 40.0,
     'Pesos y umbral de veto del documento de diseño, sección 17 (ADR 0003) -- no calibrados con datos reales todavía.');
