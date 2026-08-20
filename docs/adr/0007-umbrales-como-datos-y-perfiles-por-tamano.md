# 0007 - Umbrales de puntuación como datos, con perfiles por tamaño

## Estado
Aceptado

## Contexto
Hasta ahora los umbrales de puntuación vivían en `UmbralesIniciales.java`
y `MuestrearInstanciaServicio` los leía de ahí en cada ciclo. El javadoc de
`Umbral` afirmaba *"vive en tabla (monitor_umbral), no en código"*, pero
esa tabla nunca se leyó ni se escribió — lo admitían los comentarios de
V4 y de `JdbcRepositorioCalibracion`.

Eso bloqueaba dos cosas a la vez:

**El Módulo B (calibración).** Es la parte que más pesa en la evaluación y
la única que no se resuelve escribiendo código: hay que dejar corriendo la
línea base (B1), sacar percentiles (B2) y ajustar (B4). Con los umbrales
compilados, "ajustar" significaba editar Java, recompilar y redesplegar
para cada iteración de calibración.

**El requisito "paramétrico".** Las notas de clase insisten en que el
monitor debe adaptarse al tamaño de la base: *"una base de datos pequeña
no tiene los mismos umbrales que una grande"*. No estaba en el `.odt`
formal, así que no aparecía en la auditoría de alcance, pero es un
requisito explícito del profesor.

Además, el esquema de V1 tenía dos problemas de diseño:

1. Mezclaba en una fila los dos conceptos de umbral que el dominio ya
   separa: la normalización a 0-100 (`calibracion.Umbral`) y la
   clasificación en niveles de severidad con histéresis y confirmación
   (`alertas.UmbralAlerta`).
2. Ataba los umbrales a `calibracion_id` por clave foránea. Como
   `JdbcRepositorioCalibracion.registrar()` cierra la calibración vigente
   e inserta una fila nueva cada vez que cambian los **pesos**, los
   umbrales habrían quedado huérfanos al recalibrar y el sistema habría
   vuelto en silencio a los valores de código.

## Decisión
Tabla nueva `monitor_umbral_puntuacion` (V8), 1:1 con el record `Umbral`,
más un puerto `RepositorioUmbrales` y su adaptador `JdbcRepositorioUmbrales`.
`monitor_umbral` se elimina (nunca tuvo datos).

**Los umbrales no cuelgan de la calibración.** Pesos y umbrales son ejes de
calibración independientes: cambiar el peso de memoria no invalida el
umbral de `pga_uso_pct`. La clave de la tabla es
`(perfil, grupo, variable)`.

**`GrupoUmbral`, no `Componente`, es la clave de agrupación.** PROCESOS se
puntúa en dos pasadas (IP_usuarios / IP_fondo, ADR 0006), así que los
cuatro grupos son `PROCESOS_USUARIOS`, `PROCESOS_FONDO`, `MEMORIA` y
`ARCHIVOS` — uno por cada llamada a `CalculadorComponente.calcular()`.

**Perfiles con herencia por variable.** `monitor_instancia.perfil` toma uno
de `ESTANDAR | PEQUENA | MEDIANA | GRANDE`. La consulta resuelve cada
variable individualmente: usa la fila del perfil propio si existe, y cae a
la de `ESTANDAR` si no (`DISTINCT ON` ordenado por coincidencia de perfil).
Así un perfil redefine solo lo que de verdad cambia con el tamaño
(`util_procesos_pct`) y hereda lo que no (`a2_datafiles_offline` es
igual de grave en cualquier base).

**Solo `ESTANDAR` viene sembrado**, con los valores de diseño copiados de
`UmbralesIniciales`. Los otros tres perfiles quedan vacíos a propósito:
inventar umbrales para "base pequeña" sin haber medido ninguna sería
exactamente lo que este proyecto evita en todo lo demás. Diferenciarlos es
el resultado que debe producir B3, no una suposición previa.

**`UmbralesIniciales` sobrevive con dos papeles nuevos**: semilla de la
tabla y respaldo por grupo si la tabla no trae uno (mismo patrón que
`Calibracion.inicial()` frente a `monitor_calibracion`).

## Consecuencias
- (+) Calibrar pasa a ser un `UPDATE`, no un release. B4 deja de estar
  acoplado al ciclo de despliegue.
- (+) La columna `fuente` obliga a registrar de dónde salió cada número
  (valor de diseño / percentil observado / límite duro / prueba de
  estrés) — la trazabilidad que pide B4 explícitamente.
- (+) El requisito "paramétrico" queda cubierto estructuralmente, aunque
  todavía no haya datos para diferenciar los perfiles.
- (+) Restricciones `CHECK` en la tabla atrapan un umbral inconsistente
  (`LINEAL_INVERTIDA` con `critico <= ok`) al escribir el `UPDATE`, no
  tres horas después en medio de un ciclo de muestreo, que es cuando
  `Normalizador` lanzaría `IllegalArgumentException`.
- (−) La semilla SQL y `UmbralesIniciales` son dos copias de los mismos
  números y pueden divergir en silencio. Mitigado con
  `JdbcRepositorioUmbralesIT.la_semilla_de_v8_coincide_exactamente_con_umbrales_iniciales`,
  que lo convierte en un fallo de build — pero es un IT, así que **no
  corre en CI** (no hay Postgres en el runner). Hay que correrlo a mano
  al tocar cualquiera de los dos.
- (−) Los umbrales de **alerta** (`AlertasIniciales` / `UmbralAlerta`)
  siguen en código. Necesitan su propia tabla; este ADR no los cubre.
- (−) Una consulta más por ciclo de muestreo. Irrelevante a 60s de
  intervalo; si algún día molesta, es cacheable por perfil.
- (−) `PerfilInstancia.desde()` degrada cualquier valor desconocido a
  `ESTANDAR` en vez de fallar. Es deliberado (el `CHECK` de la tabla ya
  impide escribir basura), pero significa que un perfil mal escrito se
  comporta como estándar sin avisar.

## Alternativas consideradas
- **Reutilizar `monitor_umbral` de V1.** Habría exigido migrar un esquema
  que mezcla dos conceptos del dominio y arrastrar la FK a
  `calibracion_id` que rompe al recalibrar. Como la tabla nunca tuvo
  datos, rehacerla salía más barato que corregirla.
- **Un perfil = un juego completo de umbrales (sin herencia).** Más simple
  de consultar, pero obliga a duplicar las 16 filas por perfil y a
  mantener sincronizadas las variables que no dependen del tamaño. La
  herencia por variable cuesta un `DISTINCT ON` y evita ese problema.
- **Sembrar los tres perfiles con valores plausibles.** Habría hecho la
  demo más vistosa, pero son números inventados presentados como
  calibración — justo lo que el resto del proyecto marca como "valores de
  diseño, NO calibrados".
- **Versionar los umbrales con `vigente_desde`/`vigente_hasta`** como se
  hace con la calibración de pesos. Da historial de calibración, pero
  complica la consulta y todavía no hay ninguna calibración real que
  historiar. La columna `fuente` cubre la necesidad inmediata de
  trazabilidad; versionar se puede añadir después sin romper nada.
