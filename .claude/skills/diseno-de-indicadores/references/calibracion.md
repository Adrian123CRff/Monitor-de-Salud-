# Calibración de umbrales, histéresis y ciclo de vida de las alertas

## Parte 1 — Calibrar umbrales

### Por qué no se pueden inventar

Un umbral inventado tiene dos fallos. El práctico: casi siempre está mal, y produce
o alertas constantes (demasiado bajo) o silencio durante incidentes reales
(demasiado alto). El académico: no se puede defender. "¿Por qué 85 %?" es la
pregunta más predecible de la evaluación y "nos pareció razonable" es la peor
respuesta posible.

### Procedimiento

**Paso 1 — Línea base.** Muestrea 1–2 semanas en condiciones normales, con el
monitor guardando crudos y sin evaluar umbrales todavía. Necesitas cubrir la
variación natural: días laborales y fines de semana, horas pico y madrugada. Una
línea base de dos días de un martes tranquilo te dará umbrales que se disparan cada
lunes.

**Paso 2 — Percentiles.**

```sql
SELECT
  ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY valor), 2) AS p50,
  ROUND(PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY valor), 2) AS p90,
  ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY valor), 2) AS p95,
  ROUND(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY valor), 2) AS p99,
  ROUND(MAX(valor), 2) AS maximo,
  COUNT(*) AS muestras
FROM (
  SELECT p1_procesos_actuales * 100 / limite_procesos AS valor
  FROM   monitor_procesos
  WHERE  instancia_id = :instancia
    AND  muestreado_en BETWEEN :desde AND :hasta
);
```

**Paso 3 — Cortes iniciales.**

| Umbral | Valor | Razonamiento |
|---|---|---|
| `ok` | p90 | El 90 % del tiempo normal está por debajo: no alertar aquí |
| `advertencia` | p95 | Empieza a salirse de lo habitual |
| `alto` | p99 | Claramente inusual |
| `critico` | p99 o límite duro | Lo que sea menor |

Cuando existe un límite duro (100 % de un tablespace, `PGA_AGGREGATE_LIMIT`), ese
límite manda sobre el percentil. No tiene sentido poner el crítico de espacio en el
p99 observado si el p99 es 45 %: el hecho relevante es que a 100 % la base se
detiene.

**Paso 4 — Pruebas de estrés.** Los percentiles describen lo normal, no lo malo. Hay
que ver el extremo a propósito. Con Oracle en Docker puedes provocarlo sin riesgo:

```sql
-- Presión de procesos: abrir muchas sesiones
-- (desde un script: N conexiones concurrentes que solo duermen)

-- Presión de PGA: forzar ordenamientos grandes que no quepan en work area
ALTER SESSION SET workarea_size_policy = MANUAL;
ALTER SESSION SET sort_area_size = 65536;      -- deliberadamente pequeño
SELECT * FROM (SELECT ... FROM tabla_grande ORDER BY columna_no_indexada);

-- Presión de espacio: llenar un tablespace pequeño
CREATE TABLESPACE prueba_lleno
  DATAFILE 'prueba.dbf' SIZE 10M AUTOEXTEND OFF;
CREATE TABLE relleno TABLESPACE prueba_lleno AS
  SELECT * FROM all_objects;   -- repetir hasta ORA-01653
```

Anota qué valores alcanzan las variables en cada escenario. Si tu umbral crítico
está muy por encima de lo que ocurre en un fallo real, nunca se disparará.

**Paso 5 — Validar contra incidentes conocidos.** Si tienes registro de un problema
real, recalcula el histórico con los umbrales candidatos y comprueba que el monitor
lo habría detectado. Es la validación más convincente que existe, y solo es posible
si guardaste los valores crudos.

**Paso 6 — Documentar la fuente.** En `MONITOR_UMBRAL.fuente`, siempre:

```
'p95 observado 12-26 mayo 2026, 40 320 muestras'
'límite duro: PGA_AGGREGATE_LIMIT'
'Oracle Database Performance Tuning Guide, cap. 14'
'prueba de estrés 3 jun: el valor alcanzó 97 % antes del ORA-01653'
```

### Matriz de confusión: medir si los umbrales sirven

Con un periodo etiquetado (sabes cuándo hubo problemas reales) puedes evaluar los
umbrales como un clasificador:

|  | Problema real | Sin problema |
|---|---|---|
| **Alerta** | Verdadero positivo | Falso positivo |
| **Sin alerta** | Falso negativo | Verdadero negativo |

En monitoreo, **los falsos positivos son más caros de lo que parecen**. No porque
cada uno cueste mucho, sino porque acumulados destruyen la confianza en el sistema:
después de veinte alertas falsas nadie mira la vigesimoprimera, que es la real.
Es el problema del pastorcito mentiroso, y es la causa más común de que un sistema
de monitoreo técnicamente correcto acabe apagado.

Un análisis de este tipo, aunque sea sobre pocos días, es material de primera para
el capítulo de resultados del informe.

---

## Parte 2 — Histéresis y estabilidad

### El problema

Umbral en 90 %. La métrica oscila entre 89.8 y 90.2. Cada muestra cruza el corte en
una dirección distinta: alerta, cierre, alerta, cierre. Con muestreo cada 30
segundos son 120 eventos por hora sobre una situación que no cambió.

### Mecanismo 1: umbral doble

Entrada y salida distintas.

```
     ▲
 90 ─┼──── umbral de entrada  ────────────► activa la alerta
     │
 85 ─┼──── umbral de salida   ────────────► la cierra
     ▼
```

Entre 85 y 90 se mantiene el estado anterior, sea cual sea. Es el mecanismo del
termostato de una casa, por la misma razón: evitar que el sistema conmute sin parar
alrededor del punto de consigna.

```java
public Nivel evaluar(double valor, Nivel nivelActual, Umbral u) {
    double entrada = u.valorAdvertencia();
    double salida  = u.valorAdvertencia() - u.histeresis();

    if (nivelActual == Nivel.NORMAL) {
        return valor >= entrada ? Nivel.ADVERTENCIA : Nivel.NORMAL;
    }
    return valor <= salida ? Nivel.NORMAL : nivelActual;
}
```

Cuánta histéresis: entre el 5 y el 10 % del rango del umbral es un punto de partida
razonable. Para un umbral de espacio en 90 %, salir en 85 % funciona bien.

### Mecanismo 2: confirmación temporal

No disparar hasta que N muestras consecutivas —o N de las últimas M— crucen el
umbral.

```java
public boolean confirmada(Deque<Boolean> ultimas, int requeridas, int ventana) {
    return ultimas.stream().limit(ventana).filter(b -> b).count() >= requeridas;
}
```

"3 de las últimas 5" es más robusto que "3 consecutivas": tolera una lectura
anómala en medio de una condición real, que es un caso frecuente.

El costo es latencia. Con muestreo cada 30 s y confirmación de 3, la alerta llega
90 segundos tarde. Para espacio en disco es irrelevante. Para un datafile offline
es inaceptable: **los eventos binarios y graves no llevan confirmación**, se
disparan a la primera. La confirmación es para variables continuas y ruidosas.

### Mecanismo 3: suavizado exponencial (EWMA)

```
s_t = α · x_t + (1 − α) · s_{t−1}
```

| α | Comportamiento |
|---|---|
| 0.1 | Muy suave, reacciona lento (~10 muestras de retardo) |
| 0.3 | Equilibrado — **recomendado** |
| 0.5 | Reactivo, todavía filtra algo |
| 1.0 | Sin suavizado |

```java
public double suavizar(double actual, double anterior, double alfa) {
    return alfa * actual + (1 - alfa) * anterior;
}
```

Aplícalo a la **puntuación**, no al crudo: el crudo se persiste sin tocar para poder
recalibrar. Y ojo con acumular mecanismos: EWMA con α=0.1 más confirmación de 5
muestras más histéresis amplia produce un monitor que reacciona con cinco minutos de
retraso. Elige uno o dos por variable, no los tres.

### Qué mecanismo para qué variable

| Tipo de variable | Mecanismo | Por qué |
|---|---|---|
| Espacio en tablespace | Histéresis amplia | Cambia lento; no necesita reactividad |
| Sesiones bloqueadas | Confirmación 2 de 3 | Ruidosa, pero hay que reaccionar rápido |
| Utilización de procesos | EWMA α=0.3 + histéresis | Continua y ruidosa |
| Datafile offline | **Ninguno** | Binaria y grave: dispara de inmediato |
| Presión de PGA (delta) | Confirmación 3 de 5 | Un evento aislado es ruido; un patrón no |

---

## Parte 3 — Ciclo de vida de las alertas

Una alerta es un **episodio con duración**, no un evento puntual. Es la diferencia
entre un panel legible y uno inservible.

```
        condición cruza entrada, confirmada
NORMAL ──────────────────────────────────► ABIERTA
   ▲                                          │
   │      condición baja de salida,           │
   └──────  sostenida M muestras   ───────────┘
                                          CERRADA
```

```java
public void evaluar(InstanciaId id, Muestra m, Calibracion cal) {
    for (Umbral u : cal.umbrales()) {
        double valor  = m.valor(u.variable());
        Nivel  nivel  = evaluarConHisteresis(valor, u);
        Optional<Alerta> abierta = repo.buscarAbierta(id, u.variable(), m.entidad());

        if (nivel == Nivel.NORMAL) {
            abierta.ifPresent(a -> repo.cerrar(a, m.momento()));
        } else if (abierta.isEmpty()) {
            repo.abrir(nuevaAlerta(id, u, valor, nivel, m));
        } else if (abierta.get().nivel() != nivel) {
            // Escaló o bajó: cerrar la anterior y abrir con el nuevo nivel,
            // para que el histórico conserve la progresión del episodio.
            repo.cerrar(abierta.get(), m.momento());
            repo.abrir(nuevaAlerta(id, u, valor, nivel, m));
        }
        // Misma alerta, mismo nivel: no hacer nada. Esta rama vacía
        // es la deduplicación, y es la que hace legible el panel.
    }
}
```

La clave de deduplicación es `(instancia, variable, entidad)`. La `entidad` importa:
dos tablespaces distintos al 93 % son dos alertas, no una, y quien las lea necesita
saber cuál es cuál.

### Qué hace útil el texto de una alerta

Una alerta se lee en cinco segundos y debe permitir decidir. Compara:

> ❌ "Memoria en estado crítico"
>
> ✅ "PGA: 4 sobreasignaciones en el último minuto (umbral: 1). Asignada 2.1 GB
> sobre un target de 1.8 GB. Abierta hace 18 min."

La segunda dice **qué**, **cuánto**, **contra qué umbral** y **desde cuándo**. Las
cuatro cosas caben en el modelo de datos que ya tienes.

Genera el texto desde la plantilla del umbral, no lo escribas a mano en cada punto
del código: así todas las alertas son consistentes y cambiar el formato es un solo
cambio.

### Prioridad de presentación

Con varias alertas abiertas, ordénalas por gravedad y no por hora. El orden que
funciona:

1. Nivel (CRÍTICO antes que ALTO antes que ADVERTENCIA)
2. Dentro del mismo nivel, las que vetan el estado global
3. Dentro de eso, por duración descendente

Una alerta crítica abierta hace seis horas es la primera línea del panel. Una
advertencia de hace dos minutos es la última.
