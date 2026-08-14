# 0001 - Entorno de desarrollo y simulación de multi-instancia

## Estado
Aceptado

## Contexto
Equipo de 4 (Adrian, Kenny, Sebas, Jose) sin experiencia previa en Oracle, con
menos de 3 semanas hasta una entrega cuya fecha exacta aún no anuncia el
profesor. El profesor pidió un monitor capaz de vigilar varias bases de datos
"de clientes" (ver `numero1.png`), pero instalar u obtener instancias Oracle
realmente separadas por integrante o por cliente no es viable en el tiempo
disponible.

## Decisión
Oracle Database XE 21c en contenedor Docker, ejecutado localmente por cada
integrante durante el desarrollo. Para simular "múltiples bases de datos de
clientes" se usan varias PDBs (o contenedores XE independientes si el límite
de PDBs de usuario de la edición gratuita lo impide) dentro del mismo host;
el monitor trata cada una como una instancia independiente con su propia
cadena de conexión JDBC.

## Consecuencias
- (+) Costo cero, sin depender de infraestructura de terceros ni de permisos
  de la universidad.
- (+) Control total para provocar condiciones de estrés (llenar tablespace,
  saturar procesos) y así calibrar umbrales con datos propios.
- (+) El mismo código de recolección funciona sin cambios si en el futuro las
  instancias están en hosts físicamente separados: el monitor solo ve una
  cadena de conexión.
- (-) El techo de SGA/PGA de XE es bajo; las pruebas de "presión de memoria"
  son modestas frente a una instancia de producción real. Se declara como
  limitación en el informe.
- (-) Falta validar en la práctica cuántas PDBs de usuario admite la edición
  gratuita (pendiente P5 del registro de decisiones del proyecto).

## Alternativas consideradas
- Servidor de la universidad: permisos de monitoreo probablemente
  restringidos, sin capacidad de provocar condiciones de estrés.
- Oracle Autonomous Database (free tier): varias vistas V$ necesarias están
  restringidas o no expuestas en ese servicio.
