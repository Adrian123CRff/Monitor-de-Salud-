# ADR 0004: artefacto único. Build multi-stage -- la etapa "build" compila
# todo el reactor Maven (monitor-dominio/aplicacion/infraestructura/api);
# monitor-api/pom.xml ya dispara frontend-maven-plugin (descarga su propio
# Node, corre npm install + npm run build) durante esa misma compilación,
# así que el jar final sale con el frontend React adentro sin ningún paso
# manual de npm (ver monitor-api/pom.xml y monitor-web/vite.config.ts).
#
# El contexto de build tiene que ser la raíz del repo (no monitor-api/),
# porque el reactor necesita ver los cuatro módulos backend + monitor-web.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -pl monitor-api -am install -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/monitor-api/target/monitor-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
