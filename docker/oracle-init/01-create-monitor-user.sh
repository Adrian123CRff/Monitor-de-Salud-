#!/bin/bash
# Crea el usuario de monitoreo como usuario común en CDB$ROOT (recomendado
# por la skill oracle-vistas-dinamicas para este proyecto: acceso sin
# puntos ciegos a procesos/memoria/archivos de toda la instancia).
# Se ejecuta automáticamente al primer arranque del contenedor Oracle
# (gvenzl/oracle-free corre todo lo que hay en /container-entrypoint-initdb.d).
set -e

sqlplus -s / as sysdba <<SQL
ALTER SESSION SET CONTAINER = CDB\$ROOT;
CREATE USER c##monitor IDENTIFIED BY "${MONITOR_PASSWORD}" CONTAINER=ALL;
GRANT CREATE SESSION      TO c##monitor CONTAINER=ALL;
GRANT SET CONTAINER       TO c##monitor CONTAINER=ALL;
GRANT SELECT_CATALOG_ROLE TO c##monitor CONTAINER=ALL;
EXIT;
SQL

echo "Usuario c##monitor creado en CDB\$ROOT."
