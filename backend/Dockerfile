# Multi-stage build - the final image ships only the built jar and a JRE,
# never the Maven cache or source tree. Matches the Java 21 / Spring Boot
# 3.4.2 versions this project is pinned to (see pom.xml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -o -q dependency:go-offline || mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/hardware-erp-*.jar app.jar

# Render (and most free hosts) inject PORT at runtime and expect the
# process to bind to it - map it onto this app's own SERVER_PORT property
# rather than assuming 8080 is always free to use.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-$SERVER_PORT} -jar app.jar"]
