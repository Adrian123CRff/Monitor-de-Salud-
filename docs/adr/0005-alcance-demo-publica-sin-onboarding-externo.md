# 0005 - Alcance de la publicación pública: demo controlada, sin onboarding de clientes externos

## Estado
Aceptado

## Contexto
Las notas de clase citan al profesor diciendo que "cada cliente tendrá su
propio dashboard", lo que podía interpretarse como que el sistema debía
permitir que terceros conectaran sus propias bases de datos Oracle de
producción al monitor.

## Decisión
El sistema se despliega como una demo pública cuyas instancias monitoreadas
son controladas por el equipo (las 2 simuladas del MVP, ver ADR 0001). No se
construye un flujo de onboarding que permita a un usuario externo registrar
credenciales de una base de datos ajena.

## Consecuencias
- (+) Evita tener que resolver cifrado de secretos, aislamiento por tenant y
  validación de permisos de terceros — fuera del alcance de un curso de
  administración de bases de datos y del tiempo disponible.
- (+) El concepto de "cliente" del profesor queda representado igual (una
  instancia monitoreada = un cliente simulado), sin construir la capa de
  seguridad que un onboarding real exigiría.
- (-) Si el profesor efectivamente exige onboarding real de terceros, este
  ADR debe revisarse (pendiente P1 del registro de decisiones del proyecto:
  confirmar la interpretación directamente con él).

## Alternativas consideradas
- Onboarding real de clientes externos con sus propias credenciales:
  descartado por el riesgo de seguridad que implica y por el tiempo
  disponible del equipo.
