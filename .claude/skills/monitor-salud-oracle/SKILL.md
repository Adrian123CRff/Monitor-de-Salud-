---
name: monitor-salud-oracle
description: Contexto de dominio del proyecto "Monitor de Salud de una Base de Datos Oracle" (curso EIF402) — el catálogo de variables p1-p8 / m1-m9 / a1-a8, los indicadores IP·IM·IA, el Índice de Salud de la Base de Datos (ISBD), la escala de estados y el esquema histórico MONITOR_*. Usa esta skill SIEMPRE que se mencione el monitor de salud, el ISBD, ISBD/IP/IM/IA, "índice de salud", "salud de la base de datos", monitoreo de Oracle, o cuando se trabaje en cualquier parte de este proyecto (recolección, cálculo de índices, alertas, dashboard, esquema histórico, informe o defensa del proyecto). Úsala también cuando el usuario solo diga "el monitor", "mi proyecto de bases de datos" o "el proyecto de EIF402" sin dar más contexto, porque es el vocabulario compartido de todo el sistema.
---

# Monitor de Salud de una Base de Datos Oracle (ISBD)

Este es el contexto de dominio del proyecto. Léelo antes de escribir código, diseñar
tablas, proponer consultas o redactar documentación, porque fija el vocabulario,
las convenciones numéricas y las decisiones ya tomadas. Si algo que te piden
contradice lo que está aquí, dilo explícitamente en vez de improvisar una variante
silenciosa: en este proyecto la consistencia entre documento, código y base de datos
es parte de la nota.

## 1. Qué es el sistema

Un sistema que observa una instancia Oracle, la evalúa en tres frentes —**procesos**,
**memoria** y **archivos**— y traduce métricas técnicas crudas en un número
interpretable de 0 a 100 más un conjunto de alertas accionables.

```
Oracle → Recolección → Análisis → Índice de Salud → Dashboard + Alertas
```

La tesis del proyecto: *una base de datos disponible no es necesariamente una base
de datos sana*. Puede estar arriba y a la vez ahogada en procesos, con presión de
PGA, con sesiones bloqueadas o con un tablespace al 99 %. El monitor existe para
detectar esa diferencia. No es un visor de vistas V$; es una **capa de inteligencia
sobre el SGBD**.

## 2. Convención numérica (la regla que más se rompe)

**Todo indicador del sistema es una puntuación de salud en [0, 100] donde 100 = perfecto
y 0 = crítico.** Esto aplica a IP, IM, IA e ISBD sin excepción.

Esto importa porque la fuente natural de muchas métricas tiene la polaridad opuesta:
la *utilización* de procesos (`procesos_actuales / límite × 100`) crece cuando la
salud empeora. Si mezclas una utilización con una puntuación de salud en la misma
suma ponderada, el índice queda sin sentido y nadie lo nota hasta la defensa.

Por eso el sistema mantiene dos conceptos separados y **nunca los llama igual**:

| Concepto | Nombre | Rango | Polaridad | Dónde vive |
|---|---|---|---|---|
| Valor crudo leído de Oracle | `valor` (p1, m5, a3…) | unidad nativa | variable | tabla de la variable |
| Utilización derivada | `utilizacion_pct` | 0–100 | más alto = peor | cálculo intermedio |
| Puntuación de salud | `score` / IP / IM / IA / ISBD | 0–100 | más alto = mejor | tabla de índices |

Regla práctica: si un número puede describirse con la frase "está usado al X %",
es `utilizacion_pct` y hay que invertirlo antes de agregarlo. Si puede describirse
con "está sano en un X %", es `score`.

> **Nota sobre el documento de propuesta.** La propuesta original usa `IP` con las dos
> polaridades: en la sección 7 lo define como utilización (95–100 % = CRÍTICO) y en el
> ejemplo de la sección 19 lo usa como salud (IP = 82 contribuye a un ISBD "saludable").
> Es una inconsistencia real de la propuesta, no un error de lectura. Cuando trabajes en
> el proyecto, usa siempre la convención de salud de esta tabla y, si escribes el informe,
> deja constancia de la corrección: detectar y justificar ese arreglo suma más que
> ocultarlo.

## 3. Los tres subsistemas y sus variables

El catálogo completo —descripción, vista de Oracle de origen, unidad, dirección,
frecuencia de muestreo sugerida y trampas de cada variable— está en
`references/catalogo-variables.md`. Consúltalo cuando necesites implementar,
nombrar o discutir una variable concreta. Resumen:

| Subsistema | Indicador | Variables | Núcleo de lo que mide |
|---|---|---|---|
| Procesos | **IP** | p1–p8 | Presión sobre procesos y sesiones, bloqueos, límites de recursos |
| Memoria | **IM** | m1–m9 | Ocupación de SGA, presión y sobreasignación de PGA |
| Archivos | **IA** | a1–a8 | Disponibilidad de datafiles/tempfiles/redo y espacio en tablespaces |

## 4. Fórmulas

Los indicadores de componente son combinaciones ponderadas de las puntuaciones
normalizadas de sus variables:

```
IP = Σ score(pᵢ) · wᵢ        con Σ wᵢ = 1
IM = Σ score(mᵢ) · wᵢ        con Σ wᵢ = 1
IA = Σ score(aᵢ) · wᵢ        con Σ wᵢ = 1
```

El índice global parte de los pesos propuestos:

```
ISBD = 0.30·IP + 0.35·IM + 0.35·IA          (WP + WM + WA = 1)
```

Estos pesos son la **propuesta inicial** del documento, no un resultado. Antes de
la entrega hay que justificarlos o corregirlos; `diseno-de-indicadores` explica
cómo hacerlo de forma defendible (comparación por pares / AHP).

La normalización de cada variable a su `score`, la calibración de umbrales y la
elección entre media aritmética y geométrica pertenecen a la skill
`diseno-de-indicadores`. No los reinventes aquí.

## 5. Escala de estados

| ISBD | Estado | Color |
|---|---|---|
| 90–100 | ÓPTIMO | verde |
| 75–89 | SALUDABLE | verde |
| 60–74 | ADVERTENCIA | amarillo |
| 40–59 | DEGRADADO | naranja |
| 0–39 | CRÍTICO | rojo |

Los cortes son de diseño, no valores universales de Oracle, y deben calibrarse con
observaciones reales.

## 6. El índice global no puede ocultar un crítico

Esta es la regla de negocio más importante del sistema, y la que distingue un
monitor útil de un promedio bonito. Una media ponderada permite que un componente
excelente compense uno hundido: con IP = 25, IM = 30, IA = 98 la media aritmética
da 52 y pinta "degradado" cuando en realidad la instancia está en llamas por dos
de sus tres frentes.

Por eso el sistema produce **dos salidas independientes**, y el estado no se deriva
solo del número:

```
                    ISBD
          ┌──────────┴──────────┐
          ▼                     ▼
   Puntuación 0–100      Estado + alertas
   (tendencia)           (peor caso, con veto)
```

- La **puntuación** sirve para graficar evolución y comparar momentos.
- El **estado** se calcula con reglas de veto: si cualquier componente cae por
  debajo de su corte crítico, el estado global es CRÍTICO aunque la puntuación sea
  alta, y el dashboard debe mostrar las causas concretas.

Presentar solo el promedio es el fallo de diseño que el propio documento señala en
la sección 20. `diseno-de-indicadores` desarrolla las dos mitigaciones concretas
(media geométrica ponderada y reglas de veto).

## 7. Alertas

Cada componente emite alertas propias. Toda alerta registra:
`fecha_hora`, `componente`, `variable`, `valor`, `umbral`, `nivel`, `descripcion`.

Los niveles son `NORMAL`, `ADVERTENCIA`, `ALTO`, `CRITICO`. Una alerta describe
**una variable concreta que cruzó un umbral concreto**, nunca un índice agregado:
"Tablespace USERS al 93 % (umbral 90 %)" es útil; "la memoria está mal" no lo es.

Las alertas necesitan histéresis y deduplicación o el sistema se vuelve ruido —
ver `diseno-de-indicadores`.

## 8. Persistencia histórica

El monitor guarda su historia en un esquema **separado de la instancia monitoreada**,
para no contaminar lo que observa ni perder los datos si la instancia cae.

```
MONITOR_INSTANCIA   -- qué instancia, alias, host, tipo
MONITOR_PROCESOS    -- muestras crudas p1..p8
MONITOR_MEMORIA     -- muestras crudas m1..m9
MONITOR_ARCHIVOS    -- muestras crudas a1..a8
MONITOR_INDICES     -- IP, IM, IA, ISBD, estado por instante
MONITOR_ALERTAS     -- alertas emitidas
```

El DDL completo, con tipos, claves, índices y las decisiones de diseño detrás,
está en `references/modelo-datos.md`.

**Decisión de diseño que hay que respetar:** se guardan los **valores crudos**, no
solo las puntuaciones. Si más adelante recalibras un umbral o cambias una fórmula
—y lo vas a hacer— con los crudos puedes recalcular todo el histórico; con solo las
puntuaciones, el histórico anterior queda inservible y pierdes semanas de muestreo.

## 9. Alcance por versiones

El proyecto está planteado por incrementos. Mantente en la V1 salvo que te pidan
otra cosa; ampliar el alcance antes de tener la V1 sólida es la forma más común de
que un proyecto de curso no llegue a la entrega.

| Versión | Alcance |
|---|---|
| **V1** | Procesos, memoria, archivos, ISBD, dashboard, alertas, histórico |
| V2 | + I/O, análisis de sesiones, bloqueos, SQL |
| V3 | + seguridad, backup, recuperación |
| V4 | + predicción, detección de anomalías, alertas inteligentes |

## 10. Skills relacionadas

- `oracle-vistas-dinamicas` — el SQL concreto contra V$ para obtener cada variable,
  permisos, diferencias CDB/PDB y costo de cada consulta.
- `diseno-de-indicadores` — normalización a 0–100, calibración de umbrales,
  histéresis, agregación y justificación de pesos.
- `arquitectura-monitor-oracle` — estructura del proyecto Spring Boot + React,
  reglas de dependencia, tests y CI.

## Archivos de referencia

- `references/catalogo-variables.md` — las 25 variables, una por una, con origen,
  unidad, dirección y trampas.
- `references/modelo-datos.md` — DDL del esquema MONITOR_* y decisiones de diseño.
- `references/glosario.md` — SGA, PGA, tablespace, redo log, CDB/PDB y demás
  términos, definidos como se usan en este proyecto.
