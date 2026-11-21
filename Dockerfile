# syntax=docker/dockerfile:1

# --- Stage 1: build the SPA (Angular) ---
FROM node:24 AS frontend
WORKDIR /build/frontend
# npm version matching "packageManager" in frontend/package.json
RUN npm install -g npm@12.1.0
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the jar (Spring Boot) with the SPA in static/ ---
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
COPY --from=frontend /build/frontend/dist/itcardbrawl/browser/ src/main/resources/static/
# Tests run in a separate CI job
RUN ./mvnw -B package -DskipTests \
    && cp target/*.jar /build/app.jar

# --- Stage 3: runtime image ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/ping || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
