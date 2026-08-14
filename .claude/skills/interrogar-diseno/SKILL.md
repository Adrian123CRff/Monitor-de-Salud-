---
name: interrogar-diseno
description: >-
  Interroga al usuario sin concesiones sobre un plan, un diseño o una decisión
  técnica hasta que no quede nada asumido en silencio. Construye el árbol de
  decisiones, pregunta por rondas empezando por lo que ya se puede decidir, y
  cierra con un registro de lo acordado. Usa esta skill SIEMPRE que el usuario
  diga "interrógame", "grill me", "cuestióname", "ponme a prueba",
  "qué me falta pensar", "revisa mi plan antes de implementarlo", o cuando esté
  a punto de comprometerse con una decisión de diseño que sería cara de
  revertir — elegir un método de agregación, fijar pesos o umbrales, definir un
  esquema de base de datos, escoger una arquitectura o un stack. Aplica también
  cuando el usuario presente un plan pidiendo validación en lugar de crítica, o
  cuando pida implementar algo cuyo diseño todavía tiene huecos.
---

# Interrogar un diseño

El propósito es encontrar lo que el usuario **no ha decidido pero cree que sí**.
Los planes rara vez fracasan por la decisión que se discutió; fracasan por la que
nadie notó que estaba ahí. Este método las saca a la superficie antes de que
cuesten caro.

> Método adaptado de la skill `grilling` de Matt Pocock
> ([github.com/mattpocock/skills](https://github.com/mattpocock/skills), MIT).
> La idea del árbol de decisiones con frontera es suya; esta versión está escrita
> para trabajar en español y sembrada con las decisiones abiertas del proyecto
> Monitor de Salud de Oracle.

## Cómo funciona

### 1. Construye el árbol de decisiones

Antes de preguntar nada, mapea en silencio qué hay que decidir. Cada decisión
abre sub-decisiones que solo tienen sentido una vez resuelta la de arriba.

```
¿Cómo se agrega el ISBD?
├── si es geométrica → ¿qué piso se usa para evitar ln(0)?
│                    → ¿se guarda también la aritmética para el desglose?
└── si es aritmética → ¿cómo se evita que compense un componente crítico?
```

No preguntes por las ramas de abajo antes de resolver las de arriba: la mitad
serán irrelevantes según la respuesta.

### 2. Calcula la frontera

La **frontera** son las decisiones que ya se pueden tomar ahora mismo porque
nada las bloquea. Es lo único que se pregunta en esta ronda.

### 3. Pregunta toda la frontera de golpe

Todas las preguntas de la frontera en un solo mensaje, numeradas, y **cada una
con tu recomendación y el porqué**. Preguntar de una en una convierte una
conversación de diez minutos en una de una hora, y el usuario pierde de vista el
conjunto.

Formato de cada pregunta:

```
**3. ¿El monitor se cuenta a sí mismo en p1 y p3?**

   Recomiendo: no, excluir las sesiones del monitor por `program`.
   Por qué: con un pool de 3 conexiones estás inflando el conteo con una
   constante que no depende de la carga real. Contra: es un efecto observador
   real y ocultarlo también es discutible.
   Si dices "lo que veas mejor", tomo la recomendación.
```

Lo que hace útil este formato es que el usuario puede responder "sí a todo menos
la 3" y avanzar rápido. Una pregunta sin recomendación le traslada a él todo el
trabajo, que es justo lo contrario de lo que necesita.

### 4. No le preguntes lo que puedes averiguar

Esta es la regla que más cambia la calidad del interrogatorio. Si la respuesta
está en la documentación, en el código o en una búsqueda, **búscala tú**. El
usuario solo debe decidir lo que únicamente él puede decidir: preferencias,
prioridades, restricciones de su contexto, apetito de riesgo.

- ❌ "¿Qué columnas tiene `V$RESOURCE_LIMIT`?" → búscalo
- ❌ "¿Spring Boot 4.1 soporta Java 21?" → búscalo
- ✅ "¿Prefieres un solo artefacto desplegable o front y back separados?"
- ✅ "¿Cuánto tiempo tienes hasta la entrega?"

### 5. Espera, recalcula, repite

Con las respuestas en mano, recalcula la frontera. Las decisiones que estaban
bloqueadas ahora pueden estar disponibles, y algunas ramas habrán desaparecido.
Siguiente ronda.

### 6. Termina cuando la frontera esté vacía

No cuando te canses ni cuando parezca suficiente: cuando **cada rama del árbol
esté visitada**. Entonces resume lo decidido y **pide confirmación explícita**
de que hay entendimiento compartido antes de implementar nada.

## Reglas de conducta

**Sé duro con las ideas, nunca con la persona.** El objetivo es que el plan
salga mejor, no demostrar que tiene huecos. Un interrogatorio que deja al
usuario a la defensiva deja de producir información.

**Cuestiona también lo que suena bien.** Las decisiones que nadie discute son las
que más veces resultan estar mal — precisamente porque nadie las discutió.

**Nombra los supuestos tácitos.** "Estás asumiendo que la instancia es de
inquilino único; si es un PDB dentro de un CDB, la mitad del subsistema de
memoria no aplica." Ese tipo de frase vale más que diez preguntas.

**Acepta "no sé" como respuesta válida.** Anótalo como decisión pendiente con su
bloqueante ("hay que medirlo primero") en vez de forzar una respuesta inventada.
Una decisión aplazada conscientemente es sana; una tomada al azar no.

**Registra las decisiones descartadas y por qué.** Dentro de dos meses el usuario
va a volver sobre lo mismo. Sin el registro, se rediscute desde cero.

## Salida final

Cuando la frontera se vacíe, produce un registro con esta forma:

```markdown
## Decisiones tomadas
| # | Decisión | Elección | Razón | Alternativa descartada |

## Pendientes
| # | Decisión | Qué la desbloquea |

## Supuestos que hay que validar
- ...

## Siguiente paso concreto
```

Si la conversación tocó una decisión de arquitectura de peso, ofrece convertir
esa fila en un ADR en `docs/adr/`.

## Decisiones abiertas del proyecto Monitor de Salud de Oracle

Sembrado para que el interrogatorio arranque con contexto. Repasa cuáles siguen
abiertas antes de empezar; si el usuario trae un tema distinto, construye su
árbol y usa esto solo como fondo.

**Nivel 1 — bloquean a las demás**

1. **Dónde corre la instancia Oracle a monitorear.** Docker local / servidor de
   la universidad / Autonomous. Condiciona qué vistas son accesibles, si se
   pueden provocar condiciones de estrés para calibrar, y si el subsistema de
   memoria es medible.
2. **Alcance: CDB completa o un PDB.** Determina qué indicadores son atribuibles
   y qué hay que declarar como limitación en el informe.
3. **Cuánto tiempo hay hasta la entrega.** Define si la calibración con datos
   reales cabe o si hay que entregar con umbrales provisionales y decirlo.

**Nivel 2 — dependen de las anteriores**

4. **Pesos: AHP (0.17 / 0.39 / 0.44) o los originales (0.30 / 0.35 / 0.35).**
5. **Método de agregación: geométrica, aritmética, o ambas guardadas.**
6. **Umbral de veto por componente.** El 40 propuesto es arbitrario hasta que se
   calibre.
7. **Base del histórico: Oracle o PostgreSQL.**
8. **Despliegue: un solo jar con el front dentro, o front y back separados.**

**Nivel 3 — detalle de implementación**

9. Piso para evitar `ln(0)` en la media geométrica.
10. Si el monitor se cuenta a sí mismo en p1/p3.
11. Granularidades del endpoint de histórico y política de retención.
12. Qué se muestra cuando la recolección falla: último valor, hueco, o estado
    DESCONOCIDO.

## Skills relacionadas

Cuando el interrogatorio toque el dominio, consulta `monitor-salud-oracle`;
cuando toque indicadores, `diseno-de-indicadores`; cuando toque estructura,
`arquitectura-monitor-oracle`. Este método sirve para cualquier decisión, no solo
para las de este proyecto.
