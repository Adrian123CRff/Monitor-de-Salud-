---
name: diseno-de-indicadores
description: Cómo convertir métricas técnicas crudas en índices compuestos defendibles — normalizar valores de unidades distintas a una escala 0-100 de salud, manejar la polaridad (más es peor vs. más es mejor), calibrar umbrales con percentiles observados en lugar de números inventados, aplicar histéresis y confirmación temporal para que las alertas no oscilen, elegir entre media aritmética y geométrica ponderada, añadir reglas de veto para que un índice global no oculte un componente crítico, y justificar los pesos con comparación por pares (AHP). Usa esta skill SIEMPRE que haya que definir, calcular, calibrar, ponderar o justificar cualquier indicador, índice, puntuación, umbral o alerta — incluido el ISBD del monitor de Oracle, pero también cualquier KPI, scorecard o índice compuesto en general. Aplica cuando el usuario pregunte "qué umbral pongo", "cómo combino estas métricas", "por qué el índice no detecta el problema", "cómo justifico estos pesos" o "las alertas no paran de dispararse".
---

# Diseño de indicadores compuestos

Este es el núcleo intelectual del monitor. Recolectar métricas es trabajo de
plomería; convertirlas en un número que signifique algo y que se pueda defender es
donde está el diseño.

Un índice compuesto atraviesa cuatro decisiones, y cada una puede arruinar el
resultado por su cuenta:

```
valor crudo → NORMALIZAR → puntuación 0-100 → PONDERAR → AGREGAR → VETAR → índice + estado
                  ▲                              ▲          ▲         ▲
              umbrales                        pesos      método    reglas
              calibrados                   justificados            de negocio
```

## 1. Polaridad: la decisión que precede a todo

Antes de normalizar nada hay que fijar una convención y no romperla nunca:

> **Toda puntuación del sistema está en [0, 100] donde 100 = perfectamente sano.**

El problema es que las métricas crudas no vienen así. Van en tres direcciones:

| Tipo | Ejemplo | Tratamiento |
|---|---|---|
| **Más es peor** | utilización de procesos, uso de tablespace | invertir |
| **Más es mejor** | cache hit percentage, miembros por grupo de redo | directa |
| **Óptimo intermedio** | sesiones inactivas | desviación de la línea base |

Mezclar una utilización sin invertir con una puntuación de salud produce un índice
que parece razonable y está mal. Es un error silencioso: nada falla, los números se
ven plausibles, y el sistema entero miente.

> **Este error está en el documento de propuesta del proyecto.** En la sección 7,
> `IP = procesos_actuales / límite × 100` con 95–100 % = CRÍTICO — o sea, más alto
> es peor. En el ejemplo de la sección 19, `IP = 82` contribuye a un ISBD
> "saludable" — o sea, más alto es mejor. Es el mismo símbolo con dos polaridades
> opuestas. Detectarlo, corregirlo y explicar la corrección en el informe vale más
> que cualquier otra cosa que puedas escribir sobre los indicadores.

Defensas prácticas contra este error:

- Nombra distinto lo que es distinto: `utilizacion_pct` nunca se llama `score`.
- Declara la dirección **como dato**, en la tabla de umbrales (columna `invertir`),
  no en un `if` disperso por el código.
- Valida el rango en el constructor del objeto `Indicador`, para que un valor mal
  polarizado falle en el punto exacto del error.
- Escribe un test que verifique que 95 % de utilización produce una puntuación baja.

## 2. Normalización

Cómo pasar de un valor crudo a una puntuación de salud. Las funciones concretas,
con sus fórmulas y cuándo usar cada una, están en `references/normalizacion.md`.
Resumen de las cuatro que necesitas:

| Función | Para qué | Forma |
|---|---|---|
| **Lineal invertida con banda muerta** | La mayoría de las utilizaciones | 100 hasta `ok`, baja recta hasta 0 en `critico` |
| **Por tramos (piecewise)** | Cuando ya existen categorías establecidas | Mapea cada tramo a un rango de puntuación |
| **Directa** | Métricas donde más es mejor | La puntuación es el valor, o una recta creciente |
| **Penalización discreta** | Eventos binarios (datafile offline) | 100 menos N puntos por evento, con piso |

La lineal con banda muerta cubre el 80 % de los casos y es la que hay que usar por
defecto:

```
             valor ≤ ok        →  100
        ok < valor < critico   →  100 × (critico − valor) / (critico − ok)
             valor ≥ critico   →  0
```

La **banda muerta** —la zona plana antes de `ok`— es lo que evita que el índice
oscile con ruido irrelevante. Sin ella, la utilización pasando de 12 % a 18 % mueve
la puntuación y no debería: ambos valores son igual de sanos, y un índice que se
mueve cuando nada cambia entrena a la gente a ignorarlo.

## 3. Calibración de umbrales

Un umbral inventado es una opinión disfrazada de número. Cuando en la defensa
pregunten "¿por qué 85 % y no 80 %?", hay tres respuestas válidas y una mala.

**Válidas:**

1. **Límite duro del sistema.** "A partir del 100 % el tablespace no admite más
   datos." No requiere calibración, es un hecho.
2. **Percentil observado.** "Es el percentil 95 de tres semanas de operación
   normal: por encima de ese valor, la instancia está fuera de su comportamiento
   habitual." Es la respuesta más sólida.
3. **Criterio experto documentado.** "La guía de Oracle recomienda X." Válida si se
   cita la fuente.

**Mala:** "nos pareció razonable."

El procedimiento completo —recolectar la línea base, calcular percentiles, elegir
cortes, validar contra incidentes conocidos, iterar— está en
`references/calibracion.md`. La versión corta:

```
1. Muestrear 1-2 semanas en condiciones normales, sin tocar umbrales.
2. Calcular p50, p90, p95, p99 de cada variable.
3. umbral_ok = p90 ; umbral_advertencia = p95 ; umbral_critico = p99 o el límite duro.
4. Provocar condiciones de estrés a propósito y ver dónde caen realmente.
5. Ajustar y documentar la fuente de cada umbral.
```

El paso 4 es el que separa un trabajo bueno de uno excelente, y es la razón para
usar Oracle en Docker en lugar de una instancia compartida: puedes llenar un
tablespace, abrir 300 sesiones o forzar presión de PGA a propósito. Sin poder
provocar el extremo, calibrar es adivinar con más pasos.

**Registra la fuente de cada umbral en la propia tabla** (`MONITOR_UMBRAL.fuente`).
Reconstruirla después es imposible, y es exactamente lo que se pregunta en la
evaluación.

## 4. Histéresis: que las alertas no parpadeen

Con un umbral único, una métrica que oscila alrededor del corte genera una alerta
por muestra. Un tablespace al 89.8 %–90.2 % con umbral en 90 % produce docenas de
alertas por hora, la gente las silencia, y el monitor deja de servir. El fallo es
del diseño, no del usuario.

Tres mecanismos, complementarios:

**Umbral doble (histéresis).** El corte de entrada y el de salida son distintos.
Entra en ADVERTENCIA a 90 %, sale a 85 %. La zona intermedia mantiene el estado
anterior. Es el mecanismo del termostato, y por la misma razón.

**Confirmación temporal.** No se dispara hasta que N muestras consecutivas (o N de
las últimas M) cruzan el umbral. Elimina los picos de una sola muestra, que casi
siempre son ruido de medición.

**Suavizado exponencial (EWMA).** `s_t = α·x_t + (1−α)·s_{t−1}`. Con α ≈ 0.3 el
indicador reacciona rápido pero no salta con cada lectura. Aplícalo a la
**puntuación**, no al valor crudo: el crudo se persiste tal cual para poder
recalibrar.

Los tres, con sus parámetros y el cuándo usar cada uno, en
`references/calibracion.md`.

**Y modela la alerta como un episodio con apertura y cierre, no como un evento
puntual.** Sin eso, una condición sostenida seis horas genera cientos de filas
idénticas y el panel es inútil. Con episodios genera una fila que dice "abierta
hace 6 h", que es lo que un DBA necesita saber.

## 5. Agregación: por qué la media aritmética falla aquí

La media ponderada tiene una propiedad que en un índice de salud es un defecto:
**permite compensación total**. Un componente excelente puede tapar uno hundido.

Con IP = 25, IM = 30, IA = 98 y pesos 0.30 / 0.35 / 0.35:

```
Aritmética:  0.30·25 + 0.35·30 + 0.35·98  =  52.30   → "DEGRADADO"
```

52 suena a "hay que revisarlo cuando pueda". La realidad es que dos de los tres
subsistemas están en estado crítico.

### Solución A: media geométrica ponderada

```
ISBD = IP^wp × IM^wm × IA^wa
```

Sobre los mismos números: **42.98**, casi diez puntos más abajo y en la banda
correcta.

La propiedad que la hace apropiada: **penaliza el desequilibrio**. Si todos los
componentes son iguales, coincide con la aritmética (82, 82, 82 → 82 en ambas). A
medida que se separan, la geométrica cae más. Y en el extremo, si un componente
tiende a 0, el índice tiende a 0 sin importar los demás — que es justo la semántica
que quieres: *un sistema con un subsistema roto no está sano, por muy bien que
estén los otros dos*.

Es el mismo razonamiento por el que el Índice de Desarrollo Humano de la ONU cambió
de media aritmética a geométrica en 2010: para que un país no pudiera compensar
una esperanza de vida pésima con un ingreso alto. Citar ese precedente en el informe
le da respaldo académico inmediato a la decisión.

Cuidado con un detalle: si un componente vale exactamente 0, el producto es 0 y el
índice se satura. Usa un piso pequeño (0.1 en lugar de 0) para conservar algo de
gradiente, o acepta la saturación como comportamiento deseado y documéntalo.

### Solución B: reglas de veto

Independientes del método de agregación, y complementarias:

```
si algún componente < umbral_critico_componente (p. ej. 40):
      estado = CRITICO
      estado_por_veto = true
      causas = [componentes que dispararon el veto]
```

**La puntuación y el estado son dos salidas distintas.** La puntuación sirve para
graficar tendencias; el estado sirve para decidir. Que un ISBD de 78 venga
acompañado de estado CRÍTICO no es una contradicción: es el sistema haciendo
exactamente lo que la sección 20 de la propuesta pide.

Para que el dashboard no parezca roto, el veto debe ir siempre acompañado de sus
causas: "CRÍTICO — memoria en 30 (veto), procesos en 25 (veto)".

**Recomendación para este proyecto: usa las dos.** Media geométrica para la
puntuación, reglas de veto para el estado. Y aplica el mismo principio *dentro* de
cada componente: IA debe usar el **peor** tablespace, no el promedio de todos.

Comparaciones numéricas completas y casos límite en `references/agregacion.md`.

## 6. Justificar los pesos

Los pesos 0.30 / 0.35 / 0.35 de la propuesta son un punto de partida honesto, no un
resultado. Tres formas de convertirlos en algo defendible, de menor a mayor rigor:

**Por impacto en disponibilidad.** Ordena los componentes por cuánto se acerca su
fallo a detener la base. Un tablespace lleno la detiene; presión de PGA la degrada;
muchas sesiones inactivas casi no molestan. Es intuitivo y suficiente para muchos
contextos.

**Comparación por pares (AHP).** Compara los componentes de dos en dos en una escala
de 1 a 9, construye la matriz, calcula el autovector principal y —esto es lo
importante— **verifica la consistencia** con el índice CR. Si CR < 0.10 tus juicios
son coherentes; si no, te contradijiste y hay que revisar. Es un método publicado
(Saaty), reproducible y con validación interna: exactamente lo que se puede defender
en un tribunal académico. El procedimiento completo con el ejemplo numérico está en
`references/agregacion.md`.

**Análisis de sensibilidad.** Recalcula el histórico con varios juegos de pesos y
mira cuánto cambian las conclusiones. Si el estado apenas cambia entre 0.30/0.35/0.35
y 0.25/0.40/0.35, la elección exacta importa poco y puedes decirlo con datos —lo
cual es un resultado tan válido como cualquier otro y mucho más honesto que fingir
precisión. Este análisis es barato de hacer si guardaste los valores crudos, y es
imposible si guardaste solo las puntuaciones.

## 7. Errores frecuentes

**Promediar cuando deberías tomar el peor.** Doce tablespaces al 40 % y uno al 99 %
promedian 45 %. El agregado correcto para "espacio disponible" es el máximo de
utilización, no la media.

**Normalizar un contador acumulado.** `over allocation count` solo crece desde el
arranque de la instancia. Normalizado tal cual, el índice se degrada
permanentemente tras el primer episodio. Usa la delta entre muestras.

**Umbrales sin banda muerta.** El índice se mueve con ruido, la gente aprende a
ignorarlo.

**Un solo umbral para entrada y salida.** Alertas que parpadean.

**Guardar solo la puntuación.** Recalibrar invalida todo el histórico. Guarda
siempre el crudo.

**Recortar valores que exceden el 100 %.** Si la PGA asignada supera su target, ese
exceso *es* la señal. Recortarlo a 100 borra justo lo que buscabas.

**Confundir precisión con exactitud.** Un ISBD con dos decimales sugiere una
precisión que los umbrales calibrados a ojo no respaldan. Un decimal es suficiente
y más honesto.

> **De paso, otro hallazgo del documento de propuesta:** el ejemplo de la sección 19
> calcula `0.30(82) + 0.35(74) + 0.35(91)` y da 82.75. La suma correcta es **82.35**
> (24.60 + 25.90 + 31.85). Es un error aritmético menor, pero corregirlo en el
> informe demuestra que verificaste los números en lugar de copiarlos.

## Archivos de referencia

- `references/normalizacion.md` — las cuatro funciones con fórmulas, código y
  criterios de elección.
- `references/calibracion.md` — procedimiento de línea base, percentiles,
  histéresis, confirmación temporal, EWMA y ciclo de vida de las alertas.
- `references/agregacion.md` — aritmética vs. geométrica con números, reglas de
  veto, AHP paso a paso y análisis de sensibilidad.

## Skills relacionadas

- `monitor-salud-oracle` — las variables concretas y sus direcciones.
- `oracle-vistas-dinamicas` — de dónde salen los valores crudos.
- `arquitectura-monitor-oracle` — dónde vive este código (en el dominio, en Java
  puro, con tests unitarios rápidos).
