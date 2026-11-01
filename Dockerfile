FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run check

FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build/backend-java
COPY backend-java/pom.xml ./
COPY backend-java/maven-settings.xml /build/maven-settings.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /build/maven-settings.xml -B -DskipTests -Dspotless.skip=true dependency:resolve
COPY backend-java/ ./
COPY --from=frontend /build/frontend/dist /build/frontend/dist
RUN --mount=type=cache,target=/root/.m2 mvn -s /build/maven-settings.xml -B -Pfrontend verify

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 readquest && useradd --uid 10001 --gid readquest --no-create-home readquest
WORKDIR /app
COPY --from=backend /build/backend-java/target/readquest-backend-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/private_uploads && chown -R readquest:readquest /app
USER readquest
EXPOSE 8080 9091
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:9091/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
