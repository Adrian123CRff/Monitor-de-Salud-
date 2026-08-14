# Agregación, reglas de veto y justificación de pesos

## Parte 1 — Aritmética vs. geométrica

### El problema de la compensación

La media ponderada permite que un componente excelente compense uno hundido. En un
índice de bienestar eso puede ser deseable; en un índice de salud de un sistema, no:
una base de datos con el subsistema de memoria en estado crítico no está sana por
mucho que los archivos estén perfectos.

### Las dos fórmulas

```
Aritmética ponderada:   ISBD = Σ wᵢ · Iᵢ
Geométrica ponderada:   ISBD = Π Iᵢ^wᵢ  =  exp( Σ wᵢ · ln Iᵢ )
```

En código, la versión con logaritmos es la que hay que usar: evita desbordamiento
al multiplicar y es numéricamente más estable.

```java
public double geometricaPonderada(Map<Componente, Double> indicadores,
                                  Map<Componente, Double> pesos) {
    double suma = 0;
    for (var e : indicadores.entrySet()) {
        // Piso para evitar ln(0) = -infinito. 0.1 conserva algo de gradiente
        // en la zona catastrófica en lugar de saturar en cero.
        double v = Math.max(e.getValue(), 0.1);
        suma += pesos.get(e.getKey()) * Math.log(v);
    }
    return Math.exp(suma);
}
```

### Comparación numérica

Pesos 0.30 / 0.35 / 0.35 en todos los casos:

| Caso | IP | IM | IA | Aritmética | Geométrica | Diferencia |
|---|---|---|---|---|---|---|
| Todo sano | 82 | 82 | 82 | 82.0 | 82.0 | 0.0 |
| Ejemplo de la propuesta | 82 | 74 | 91 | **82.3** | 82.0 | −0.3 |
| Dos componentes hundidos | 25 | 30 | 98 | 52.3 | **43.0** | −9.3 |
| Uno catastrófico | 0 | 100 | 100 | 70.0 | **3.2** | −66.8 |

Dos lecturas importantes:

**Con valores equilibrados, ambas coinciden.** No es un método que castigue por
sistema: solo se aparta cuando hay desequilibrio, que es exactamente cuando quieres
que se aparte.

**El caso extremo es el argumento decisivo.** Un componente en 0 con los otros dos
perfectos da 70 con aritmética — "advertencia leve" — y 3.2 con geométrica. Un
subsistema completamente caído no puede producir un índice de salud de 70. La
geométrica lo entiende; la aritmética no.

> Nota sobre el ejemplo de la propuesta: la sección 19 calcula
> `0.30(82) + 0.35(74) + 0.35(91)` y reporta 82.75. La suma correcta es **82.35**
> (24.60 + 25.90 + 31.85). Es un desliz aritmético menor; corregirlo en el informe
> demuestra que verificaste los números.

### Respaldo académico

El **Índice de Desarrollo Humano** del PNUD cambió de media aritmética a geométrica
en 2010, y por esta misma razón: con la aritmética, un país podía compensar una
esperanza de vida pésima con un ingreso alto, y el índice resultante no describía a
nadie. La geométrica hace que los tres componentes tengan que estar razonablemente
bien para producir un valor alto.

Citarlo en el informe le da a la decisión un respaldo que trasciende el proyecto:
no es una ocurrencia, es un problema conocido con una solución establecida en la
literatura de indicadores compuestos.

### Cuándo la aritmética sigue siendo mejor

- Cuando los componentes son **genuinamente sustituibles** (no es el caso aquí).
- Cuando hay que **explicar la contribución** de cada componente al total. La
  aritmética descompone limpiamente: "de los 82 puntos, memoria aporta 25.9". La
  geométrica no admite esa lectura.

Una opción práctica: calcula y guarda **las dos**. Usa la geométrica como índice
oficial y muestra la aritmética en el desglose del dashboard como "contribución por
componente". Cuesta una columna más y hace el dashboard mucho más explicativo.

---

## Parte 2 — Reglas de veto

Independientes del método de agregación y complementarias a él. Cubren lo que
ninguna media puede resolver: que ciertas condiciones son inaceptables por sí
mismas, sin importar el promedio.

```java
public Isbd combinar(Indicador ip, Indicador im, Indicador ia, Calibracion cal) {
    double puntuacion = cal.metodo() == GEOMETRICA
        ? geometricaPonderada(...)
        : aritmeticaPonderada(...);

    Estado estado = Estado.desdePuntuacion(puntuacion);
    List<String> causas = new ArrayList<>();

    // Veto 1: un componente por debajo de su corte crítico
    for (Indicador i : List.of(ip, im, ia)) {
        if (i.puntuacion() < cal.umbralVetoComponente()) {
            causas.add("%s en %.0f (veto: < %.0f)".formatted(
                i.componente(), i.puntuacion(), cal.umbralVetoComponente()));
        }
    }

    // Veto 2: condiciones absolutas, sin grado posible
    causas.addAll(cal.vetosAbsolutos().stream()
        .filter(v -> v.seCumple(ip, im, ia))
        .map(Veto::descripcion)
        .toList());

    boolean vetado = !causas.isEmpty();
    return new Isbd(momento, puntuacion,
                    vetado ? Estado.CRITICO : estado,
                    ip, im, ia, vetado, causas);
}
```

**Vetos absolutos para este proyecto** — condiciones donde no existe un "un poco":

| Condición | Por qué veta |
|---|---|
| Algún datafile en `RECOVER` | La base necesita recuperación; no hay grado |
| Algún datafile `OFFLINE` no planificado | Hay datos inaccesibles |
| Algún tablespace ≥ 98 % | Detención inminente, cuestión de minutos u horas |
| Algún miembro de redo `INVALID` | Riesgo directo sobre la recuperabilidad |
| Fallo de recolección sostenido | El monitor no ve nada: no puede afirmar que hay salud |

El último es el más fácil de olvidar y de los más importantes. Si el monitor no
puede leer la instancia, el estado correcto es DESCONOCIDO o CRÍTICO, nunca el
último valor bueno. Un monitor que muestra verde porque perdió la conexión es peor
que no tener monitor: transmite una seguridad falsa justo cuando algo va mal.

### Presentarlo sin que parezca un error

Un ISBD de 78 junto a un estado CRÍTICO parece un bug si no se explica. La interfaz
tiene que mostrar las dos cosas juntas:

```
┌────────────────────────────────────────┐
│              ISBD  78.4                │
│           ⛔ CRÍTICO (por veto)         │
│                                        │
│  Causas:                               │
│  • MEMORIA en 30 (veto: < 40)          │
│  • Tablespace USERS al 98.2 %          │
└────────────────────────────────────────┘
```

El número sigue siendo útil para la gráfica de tendencia. El estado es lo que dicta
la acción. Son dos salidas distintas del mismo cálculo, y presentarlas así es
justamente lo que la sección 20 de la propuesta pide.

### El mismo principio dentro de cada componente

IA no debe promediar los tablespaces: debe usar el **peor**. Doce al 40 % y uno al
99 % promedian 45 % y suenan tranquilos mientras la base está a punto de detenerse.
El agregado correcto para "espacio disponible" es el máximo de utilización, con el
conteo de tablespaces en riesgo como información complementaria.

---

## Parte 3 — Justificar los pesos con AHP

El *Analytic Hierarchy Process* (Saaty, 1980) convierte juicios subjetivos en pesos
numéricos **y verifica que esos juicios sean coherentes entre sí**. Esa verificación
es lo que lo hace defendible: no elimina la subjetividad, pero la hace explícita y
comprobable.

### Paso 1 — Comparación por pares

Escala de Saaty: 1 = igual importancia, 3 = moderadamente más, 5 = fuertemente más,
7 = muy fuertemente más, 9 = extremadamente más (2, 4, 6, 8 = valores intermedios).

Juicios para este proyecto:

- **Archivos vs. Procesos = 3.** Un tablespace lleno detiene la base; una
  utilización alta de procesos la degrada. El fallo de archivos es más severo.
- **Memoria vs. Procesos = 2.** La presión de PGA tiene impacto medible en tiempos
  de respuesta; los procesos suelen tener más margen antes de doler.
- **Archivos vs. Memoria = 1.** Comparables: uno detiene, el otro degrada de forma
  sostenida.

Matriz (filas y columnas en orden Procesos, Memoria, Archivos):

```
        P     M     A
P  [   1    1/2   1/3  ]
M  [   2     1     1   ]
A  [   3     1     1   ]
```

La diagonal es 1 por definición y `a_ji = 1/a_ij`.

### Paso 2 — Calcular los pesos

Aproximación por media geométrica de filas, que es la habitual y suficientemente
precisa:

```
gm(P) = (1 · 0.5 · 0.333)^(1/3) = 0.5503
gm(M) = (2 · 1   · 1    )^(1/3) = 1.2599
gm(A) = (3 · 1   · 1    )^(1/3) = 1.4422
                          suma  = 3.2524

wP = 0.5503 / 3.2524 = 0.169
wM = 1.2599 / 3.2524 = 0.387
wA = 1.4422 / 3.2524 = 0.443
```

### Paso 3 — Verificar consistencia

Este paso es lo que distingue AHP de repartir porcentajes a ojo.

```
A·w = [0.5107, 1.1692, 1.3384]
λᵢ  = (A·w)ᵢ / wᵢ = [3.0183, 3.0183, 3.0183]
λmax = 3.0183

CI = (λmax − n) / (n − 1) = (3.0183 − 3) / 2 = 0.0091
RI (n=3) = 0.58
CR = CI / RI = 0.0158
```

**CR = 0.016 < 0.10 → los juicios son consistentes.** Si hubiera salido por encima
de 0.10, significaría que te contradijiste (por ejemplo: A más importante que B, B
más que C, pero C más que A) y habría que revisar la matriz antes de usar los pesos.

Índices aleatorios de referencia: n=3 → 0.58, n=4 → 0.90, n=5 → 1.12.

### Paso 4 — Comparar con la propuesta inicial

| | Procesos | Memoria | Archivos |
|---|---|---|---|
| Propuesta original | 0.30 | 0.35 | 0.35 |
| AHP | **0.17** | **0.39** | **0.44** |

El método dice que procesos pesa bastante menos de lo que la propuesta asumía.
Es un resultado, no un fallo: tienes dos juegos de pesos y un procedimiento
reproducible que explica de dónde sale el segundo.

Aquí hay una decisión que tomar y documentar, y ambas opciones son defendibles:
adoptar los pesos de AHP, o mantener los originales argumentando que los juicios de
la matriz merecen revisión. Lo que no es defendible es no haber hecho el ejercicio.

Un mismo script en el repositorio que calcule pesos, λmax y CR a partir de la matriz
hace todo esto reproducible y verificable — y eso es lo que un evaluador valora.

---

## Parte 4 — Análisis de sensibilidad

Pregunta clave: **¿cuánto importan realmente los pesos?** Muchas veces, menos de lo
que la discusión sugiere. Comprobarlo con datos es más honesto que defender una
precisión que no existe.

**Escenario sano (IP=82, IM=74, IA=91):**

| Pesos | Aritmética | Geométrica |
|---|---|---|
| Propuesta 0.30/0.35/0.35 | 82.3 | 82.0 |
| AHP 0.17/0.39/0.44 | 82.9 | 82.5 |
| Iguales 0.33/0.33/0.33 | 82.3 | 82.0 |
| Procesos-pesado 0.50/0.25/0.25 | 82.2 | 82.0 |

**Escenario degradado (IP=25, IM=30, IA=98):**

| Pesos | Aritmética | Geométrica |
|---|---|---|
| Propuesta 0.30/0.35/0.35 | 52.3 | 43.0 |
| AHP 0.17/0.39/0.44 | 59.3 | 49.2 |
| Iguales 0.33/0.33/0.33 | 51.0 | 41.9 |
| Procesos-pesado 0.50/0.25/0.25 | 44.5 | 36.8 |

Tres conclusiones que dan contenido propio al capítulo de resultados:

1. **Cuando el sistema está sano, los pesos son casi irrelevantes.** Todas las
   combinaciones dan entre 82.0 y 82.9. Discutir décimas ahí es perder el tiempo.
2. **Cuando hay desequilibrio, los pesos importan mucho** — un rango de 44 a 59 en
   aritmética. Es exactamente el escenario donde la decisión se toma.
3. **La elección del método de agregación pesa más que la de los pesos.** En el
   escenario degradado, geométrica vs. aritmética mueve ~9 puntos con los mismos
   pesos, mientras que cambiar los pesos con el mismo método mueve ~7. Es un
   argumento fuerte para dedicar el esfuerzo de justificación al método antes que a
   afinar decimales en los pesos.

Este análisis es barato si guardaste los valores crudos —recalculas el histórico con
cada juego de pesos— e imposible si guardaste solo las puntuaciones. Es la
justificación práctica de esa decisión de diseño.
