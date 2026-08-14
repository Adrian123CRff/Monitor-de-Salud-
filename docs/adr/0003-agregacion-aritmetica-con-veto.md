# 0003 - Agregación del ISBD: media aritmética ponderada con regla de veto

## Estado
Aceptado

## Contexto
El documento de diseño original define
`ISBD = 0.30·IP + 0.35·IM + 0.35·IA` y en su sección 20 identifica
explícitamente el riesgo de que ese promedio ponderado oculte un componente
en estado crítico: dos componentes en rojo pueden quedar enmascarados por un
tercero muy alto.

## Decisión
El ISBD se calcula como media aritmética ponderada de IP, IM e IA, con los
pesos iniciales del documento (0.30 / 0.35 / 0.35, declarados como no
calibrados). Se añade una regla de veto: si cualquier componente —incluidos
los sub-índices IP_usuarios / IP_fondo— cae en la franja "Crítico" (0-39), el
estado global mostrado se fuerza a 🔴 CRÍTICO independientemente del valor
numérico del ISBD, y se listan las causas.

## Consecuencias
- (+) Coincide con la fórmula ya redactada en el documento formal del
  proyecto; no hay que justificar ante el profesor un cambio de fórmula.
- (+) Resuelve explícitamente el caso de enmascaramiento que el propio
  documento marca como riesgo a evitar.
- (-) El umbral de veto (40) es arbitrario hasta calibrarlo con datos reales
  de operación (pendiente P3 del registro de decisiones del proyecto).
- (-) Hay dos lecturas del estado (puntuación 0-100 y estado con veto); el
  dashboard y el informe deben explicar ambas con claridad para no parecer
  contradictorios entre sí.

## Alternativas consideradas
- Media geométrica ponderada: castiga matemáticamente los componentes bajos
  sin necesitar una regla aparte, pero exige definir un piso para evitar
  valores en 0 y se aleja de la fórmula ya escrita en el documento.
- Calcular ambas medias y guardarlas: más material comparativo para la
  defensa del proyecto, pero duplica la lógica a implementar y explicar con
  el tiempo disponible.
