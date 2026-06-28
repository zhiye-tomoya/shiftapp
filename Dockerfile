# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1: Build the Spring Boot fat jar with Gradle
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper + build scripts first so dependency resolution can be
# cached independently of source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Pre-fetch dependencies (best-effort; ignored if it can't fully resolve yet).
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy the rest of the source and build the runnable jar (skip tests in the
# image build — tests run in CI / locally).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------------------------------------------------------------------------
# Stage 2: Minimal runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy the single boot jar produced above to a stable, version-independent name.
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

# Railway injects $PORT; Spring Boot binds to it via server.port=${PORT:8080}.
EXPOSE 8080

# Respect container memory limits and bind to the injected port.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar app.jar"]
