-- ADR 0001: una sola instancia monitoreada. El planificador usa
-- monitor.instancia-id=1 (application.yml) desde el primer ciclo, y todo
-- guardar() de MuestrearInstanciaServicio inserta con ese instancia_id --
-- sin esta fila, un despliegue nuevo (base de datos vacía) falla desde el
-- primer ciclo por la FK a monitor_instancia. En este dev machine nunca se
-- notó porque los IT tests insertan su propia fila 'IT-test' a mano.
--
-- host/puerto/servicio son descriptivos: hoy nada en el código los lee para
-- decidir a dónde conectarse (eso lo fija monitor.datasource.monitoreada.jdbc-url
-- en application.yml) -- monitor_instancia es un catálogo, no configuración
-- activa todavía (ver RepositorioInstancias, pendiente).
INSERT INTO monitor_instancia (alias, host, puerto, servicio, tipo)
VALUES ('principal', 'oracle-monitoreado', 1521, 'FREE', 'PDB')
ON CONFLICT (alias) DO NOTHING;
