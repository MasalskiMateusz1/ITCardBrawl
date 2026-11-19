# syntax=docker/dockerfile:1

# --- Etap 1: build SPA (Angular) ---
FROM node:24 AS frontend
WORKDIR /build/frontend
# Wersja npm zgodna z "packageManager" w frontend/package.json
RUN npm install -g npm@12.1.0
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Etap 2: build jara (Spring Boot) z SPA w static/ ---
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
COPY --from=frontend /build/frontend/dist/itcardbrawl/browser/ src/main/resources/static/
# Testy biegna w osobnym jobie CI
RUN ./mvnw -B package -DskipTests \
    && cp target/*.jar /build/app.jar

# --- Etap 3: obraz uruchomieniowy ---
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
