# Multi-stage build - the final image ships only the built jar and a JRE,
# never the Maven cache or source tree. Matches the Java 21 / Spring Boot
# 3.4.2 versions this project is pinned to (see pom.xml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved from pom.xml alone, before the sources are copied,
# so editing a Java file does not invalidate the layer that holds ~200MB of
# downloads.
COPY pom.xml .

# BUG-BUILD-001: this used to start with `mvn -o` - offline mode - against a
# brand new image whose local repository is empty. That can only ever fail:
#
#   Cannot access central in offline mode and the artifact
#   org.springframework.boot:spring-boot-starter-parent:pom:3.4.2 has not
#   been downloaded from it before
#
# A `|| retry online` fallback was supposed to absorb it, but go-offline is
# unreliable in its own right - it resolves plugin dependencies that a plain
# build never needs, and one unreachable optional artifact fails the step.
#
# The warm-up is now a genuine optimisation rather than a requirement:
# it runs ONLINE, and `|| true` means a failure costs a slower build, never a
# broken one. Whatever it misses, the package step below fetches.
RUN mvn -B dependency:go-offline -DskipTests || true

COPY src ./src

# Not -q. When a production build fails, the log is the only thing available
# to diagnose it from, and quiet mode throws away exactly what is needed.
# Tests are skipped here on purpose - they need Testcontainers and a Docker
# daemon, which is not available inside a Docker build. CI runs them.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/hardware-erp-*.jar app.jar

# Render (and most free hosts) inject PORT at runtime and expect the
# process to bind to it - map it onto this app's own SERVER_PORT property
# rather than assuming 8080 is always free to use.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-$SERVER_PORT} -jar app.jar"]
