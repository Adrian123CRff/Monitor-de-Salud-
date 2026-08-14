# 0002 - PostgreSQL como motor del histórico

## Estado
Aceptado

## Contexto
El diseño exige conservar la evolución de IP/IM/IA/ISBD y de las alertas
(tablas `MONITOR_*`) para responder si la salud está mejorando o empeorando.
Si ese histórico vive en la misma instancia que se está monitoreando, una
caída de esa instancia se lleva consigo la evidencia justo en el momento en
que más se necesita.

## Decisión
El histórico se persiste en PostgreSQL, en un contenedor Docker separado de
la(s) instancia(s) Oracle monitoreadas.

## Consecuencias
- (+) El historial de alertas sobrevive aunque la instancia Oracle
  monitoreada esté caída o inaccesible — el escenario más importante de
  auditar.
- (+) Separa responsabilidades con claridad: Oracle es la fuente observada,
  Postgres es la memoria del monitor.
- (-) Tecnología adicional que aprender junto con Oracle, en un equipo sin
  experiencia previa en ninguna de las dos.
- (-) Cada máquina de desarrollo necesita correr dos motores de base de datos
  a la vez (vía docker-compose).

## Alternativas consideradas
- Oracle mismo, en un PDB de "administración" separado del PDB monitoreado:
  una sola tecnología para todo el equipo, pero el histórico quedaría
  expuesto al mismo tipo de falla que se supone debe registrar.
