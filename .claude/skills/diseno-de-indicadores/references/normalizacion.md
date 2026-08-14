# Funciones de normalización

Convertir un valor crudo en una puntuación de salud en [0, 100] donde 100 = sano.

## 1. Lineal invertida con banda muerta

La opción por defecto para métricas donde más es peor: utilizaciones, porcentajes
de ocupación, conteos con límite conocido.

```
                 valor ≤ ok        →  100
            ok < valor < critico   →  100 · (critico − valor) / (critico − ok)
                 valor ≥ critico   →  0
```

```
puntuación
100 ┤────────────╮
    │            ╲
 50 ┤             ╲
    │              ╲
  0 ┤               ╰──────────
    └────┬──────┬────┬─────────► valor
         0     ok  critico
```

```java
public double linealInvertida(double valor, double ok, double critico) {
    if (critico <= ok) {
        throw new IllegalArgumentException(
            "critico debe ser mayor que ok en una métrica invertida");
    }
    if (valor <= ok)      return 100.0;
    if (valor >= critico) return 0.0;
    return 100.0 * (critico - valor) / (critico - ok);
}
```

**La banda muerta** es la zona plana antes de `ok`, y es lo que hace útil esta
función. Sin ella, la utilización pasando de 12 % a 18 % mueve la puntuación cuando
no debería: ambos valores son igual de sanos. Un índice que fluctúa cuando nada
relevante cambia entrena a la gente a ignorarlo, y un indicador ignorado no vale
nada.

**Ejemplo — utilización de procesos** con `ok = 70`, `critico = 95`:

| Utilización | Puntuación | Lectura |
|---|---|---|
| 30 % | 100 | holgado |
| 70 % | 100 | límite de la banda muerta |
| 80 % | 60 | empieza a apretar |
| 90 % | 20 | serio |
| 95 % | 0 | crítico |

## 2. Por tramos (piecewise)

Cuando ya existe una clasificación por categorías que hay que respetar —como los
tramos NORMAL / ADVERTENCIA / ALTO / CRÍTICO de la propuesta— y quieres que la
puntuación sea coherente con ellos.

```java
public record Tramo(double desde, double hasta, double puntoDesde, double puntoHasta) {}

public double porTramos(double valor, List<Tramo> tramos) {
    for (Tramo t : tramos) {
        if (valor >= t.desde() && valor < t.hasta()) {
            double fraccion = (valor - t.desde()) / (t.hasta() - t.desde());
            return t.puntoDesde() + fraccion * (t.puntoHasta() - t.puntoDesde());
        }
    }
    return valor < tramos.get(0).desde() ? 100.0 : 0.0;
}
```

Para la clasificación de la sección 7 de la propuesta:

```java
List.of(
    new Tramo( 0,  70, 100, 85),   // NORMAL      → 100..85
    new Tramo(70,  85,  85, 60),   // ADVERTENCIA →  85..60
    new Tramo(85,  95,  60, 30),   // ALTO        →  60..30
    new Tramo(95, 101,  30,  0)    // CRÍTICO     →  30..0
);
```

Ventaja sobre la lineal simple: mantiene continuidad —no hay saltos bruscos en los
bordes— y a la vez respeta las categorías, de modo que el número y el color siempre
concuerdan. Un valor en el tramo CRÍTICO nunca puede producir una puntuación por
encima de 30, cosa que con una lineal ajustada de otra forma sí podría pasar y
resultaría muy difícil de explicar.

Desventaja: cuatro veces más parámetros que calibrar. Úsala solo donde las
categorías estén realmente establecidas.

## 3. Directa (más es mejor)

Para métricas donde el valor alto es lo bueno: `cache hit percentage`, miembros por
grupo de redo, porcentaje de datafiles online.

```
                 valor ≥ ok        →  100
       critico < valor < ok        →  100 · (valor − critico) / (ok − critico)
                 valor ≤ critico   →  0
```

```java
public double linealDirecta(double valor, double critico, double ok) {
    if (ok <= critico) {
        throw new IllegalArgumentException(
            "ok debe ser mayor que critico en una métrica directa");
    }
    if (valor >= ok)      return 100.0;
    if (valor <= critico) return 0.0;
    return 100.0 * (valor - critico) / (ok - critico);
}
```

Fíjate en que el orden de los parámetros se invierte respecto a la función anterior.
Es una fuente de errores real. La forma robusta de manejarlo es una sola función que
lea la dirección de la configuración:

```java
public double puntuar(double valor, Umbral u) {
    return u.invertir()
        ? linealInvertida(valor, u.valorOk(), u.valorCritico())
        : linealDirecta(valor, u.valorCritico(), u.valorOk());
}
```

Un solo punto donde la polaridad se decide, y se decide leyendo un dato de la tabla
de umbrales en lugar de recordando el orden de dos argumentos.

## 4. Penalización discreta

Para eventos binarios o contables donde cada ocurrencia es un problema en sí: un
datafile offline, un archivo inválido, un grupo de redo sin redundancia.

```java
public double penalizacion(int eventos, double puntosPorEvento, double piso) {
    return Math.max(piso, 100.0 - eventos * puntosPorEvento);
}
```

Para variables donde una sola ocurrencia ya es crítica —un datafile que necesita
recuperación—, lo correcto es la penalización total:

```java
public double criticoSiHayAlguno(int eventos) {
    return eventos > 0 ? 0.0 : 100.0;
}
```

Y una variable así debería además **vetar** el estado global. No hay grados en
"faltan archivos de la base de datos".

## 5. Desviación de la línea base

Para variables donde ni alto ni bajo es intrínsecamente malo, y lo que importa es
apartarse del comportamiento habitual. El caso típico: sesiones inactivas.

```java
public double desviacion(double valor, double media, double desviacionEstandar,
                         double sigmasCritico) {
    if (desviacionEstandar <= 0) return 100.0;   // sin variabilidad, sin señal
    double z = Math.abs(valor - media) / desviacionEstandar;
    return Math.max(0, 100.0 * (1 - z / sigmasCritico));
}
```

Con `sigmasCritico = 3`, un valor a 3 desviaciones estándar de la media puntúa 0.

Requiere una línea base ya calculada, así que solo puede usarse después del periodo
de calibración. Antes de eso, la variable queda como contexto sin puntuación — lo
cual es preferible a inventar un umbral.

Un aviso: esto asume implícitamente que la variable se distribuye de forma
aproximadamente simétrica. Muchas métricas de sistemas tienen cola larga a la
derecha, y ahí la media y la desviación estándar engañan. Si la distribución es muy
asimétrica, usa percentiles (mediana y rango intercuartílico) en lugar de media y
sigma.

## Elegir la función

```
¿El valor es un evento contable donde cada ocurrencia es un problema?
   └─ Sí → penalización discreta (o crítico-si-hay-alguno)
   └─ No ↓
¿Existe una clasificación por categorías que hay que respetar?
   └─ Sí → por tramos
   └─ No ↓
¿Hay un valor "normal" del que apartarse en ambas direcciones?
   └─ Sí → desviación de la línea base
   └─ No ↓
¿Más alto es peor?
   └─ Sí → lineal invertida con banda muerta   ← el caso más común
   └─ No → lineal directa
```

## Asignación por variable en este proyecto

| Variable | Función | ok | critico | Nota |
|---|---|---|---|---|
| `util_procesos_pct` | lineal invertida | 70 | 95 | Los tramos de la propuesta |
| `util_sesiones_pct` | lineal invertida | 70 | 95 | Suele saturarse antes que procesos |
| p6 sesiones bloqueadas | penalización | — | — | 25 pts por bloqueo, piso 0 |
| `bloqueo_max_seg` | lineal invertida | 5 | 120 | La duración importa más que el conteo |
| p7 operaciones largas | contexto | — | — | No puntúa: son legítimas |
| `Δ over_allocation` | penalización | — | — | 20 pts por evento en el intervalo |
| `pga_uso_pct` | lineal invertida | 90 | 130 | Sin recortar en 100 |
| `cache_hit_pct` (delta) | lineal directa | 90 | 50 | Más es mejor |
| `sga_libre_pct` | contexto | — | — | No puntúa: llena es normal |
| `peor_tablespace_pct` | lineal invertida | 75 | 95 | La variable más importante de IA |
| a2 datafiles offline | crítico-si-hay-alguno | — | — | Y veta el estado global |
| a7 archivos inválidos | crítico-si-hay-alguno | — | — | Y veta |
| a8 archivos en recover | crítico-si-hay-alguno | — | — | Y veta |
| `redundancia_redo` | lineal directa | 2 | 1 | 1 miembro = sin copia |

Los valores de `ok` y `critico` son **puntos de partida para calibrar**, no
resultados. Sustitúyelos por percentiles observados en cuanto tengas una línea base
— ver `calibracion.md`.

Nota sobre las tres variables marcadas "contexto": no puntuar una variable es una
decisión legítima y a veces la correcta. `sga_libre_pct` no entra en IM porque una
SGA llena es Oracle funcionando bien; incluirla produciría un monitor que marca rojo
en una instancia sana. Se muestra en el dashboard como información, sin peso en el
índice. Explicar por qué **no** incluiste algo demuestra tanto criterio como
justificar lo que sí incluiste.
