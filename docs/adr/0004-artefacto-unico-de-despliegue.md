# 0004 - Despliegue como artefacto único

## Estado
Aceptado

## Contexto
La demo pública debe desplegarse en un hosting gratuito, con el equipo
trabajando bajo presión de tiempo (menos de 3 semanas) y sin experiencia
previa en este tipo de despliegues.

## Decisión
El frontend (React) se compila a estático durante el build y el backend
(Spring Boot) lo sirve directamente, empaquetados juntos en un solo
artefacto/contenedor desplegable.

## Consecuencias
- (+) Un solo servicio que desplegar, sin configurar CORS entre dos orígenes
  distintos.
- (+) Menos piezas que puedan fallar el día de la demo o de la presentación
  ante el profesor.
- (-) El frontend no puede escalar ni desplegarse de forma independiente del
  backend. Aceptable para una demo de curso; no lo sería para un producto
  real.

## Alternativas consideradas
- Backend y frontend como dos servicios separados: más realista para un
  entorno de producción, pero exige dos pipelines de despliegue y dos URLs
  coordinadas — más piezas que pueden fallar bajo presión de tiempo.
