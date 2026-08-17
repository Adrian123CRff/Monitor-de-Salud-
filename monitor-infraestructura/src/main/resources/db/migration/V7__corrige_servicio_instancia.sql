-- Corrige el valor descriptivo de "servicio" sembrado en V6: decía 'FREE'
-- (el servicio del CDB$ROOT), pero la conexión real siempre debió apuntar
-- a 'FREEPDB1' (el PDB) -- ver el comentario en application.yml sobre el
-- punto ciego CDB/PDB. V6 ya está aplicada (Flyway la valida por checksum),
-- así que la corrección va en una migración nueva, no editando V6.
--
-- Sigue siendo solo descriptivo (ver el comentario de V6: nada en el código
-- lee esta columna para decidir a dónde conectarse todavía).
UPDATE monitor_instancia
SET servicio = 'FREEPDB1'
WHERE alias = 'principal' AND servicio = 'FREE';
