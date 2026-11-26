# syntax=docker/dockerfile:1.27.0@sha256:bde3983e9c939224420ddaf6b784cc30e09b035a4dea01f581230c50809f372e
# ============================================================================
# Ferretería "El Tornillo Feliz" — Backend (Spring Boot 4.1 + Java 21)
# ----------------------------------------------------------------------------
# Multi-stage con cache mounts de BuildKit. Compatible con:
#   - docker buildx
#   - podman compose (buildah bajo el capó)
#
# Tamaños esperados:
#   - build stage: ~1.2 GB (Gradle + JDK 21 + cache deps)
#   - runtime:     ~250 MB (JRE alpine + jar ~60 MB)
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1: build
# ----------------------------------------------------------------------------
FROM gradle:9.9.2-jdk21@sha256:f8c4f7de6ec4f367cbf7fc46db08129ea6a6e9487995bb705c61fbc20f3bc545 AS build
WORKDIR /home/gradle/project

# Cache de descriptores Gradle: solo cambia si build.gradle.kts/gradle.properties
# cambian. Cache de ~/.gradle: persiste entre builds (deps, daemon disabled).
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=cache,target=/home/gradle/.kotlin \
    gradle dependencies --no-daemon -q || true

# Código completo. Las migraciones se copian dentro del contexto porque la
# imagen las necesita para arrancar (Flyway corre antes que JPA).
COPY --chown=gradle:gradle src ./src

# Compila el jar sin tests (los IT corren en CI con docker compose separado).
RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=cache,target=/home/gradle/.kotlin \
    gradle bootJar --no-daemon -q -x test

# ----------------------------------------------------------------------------
# Stage 2: runtime
# ----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89 AS runtime

# wget para el healthcheck, curl para descargar el OTel agent
RUN apk add --no-cache wget curl tzdata && \
    addgroup -S app && adduser -S app -G app && \
    cp /usr/share/zoneinfo/America/Mexico_City /etc/localtime && \
    echo "America/Mexico_City" > /etc/timezone

WORKDIR /app

# ─── OpenTelemetry Java Agent ────────────────────────────────────────────
# Auto-instrumenta HTTP (Spring WebFilter, Tomcat), JDBC (HikariCP, Hibernate),
# R2DBC, Kafka, etc. Configurado vía env vars OTEL_* (ver docker-compose).
# Si OTEL_SDK_DISABLED=true el agent no hace nada (ahorra CPU en dev local).
ARG OTEL_AGENT_VERSION=2.34.0
# SHA-256 del asset oficial del release v2.34.0.
ARG OTEL_AGENT_SHA256=da44154cea128257779c8a782b9651fd7c0c6aa177cd3810f9a867ac8509555d
RUN curl -fsSL -o /opt/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
 && echo "${OTEL_AGENT_SHA256}  /opt/opentelemetry-javaagent.jar" | sha256sum -c - \
 && chmod 644 /opt/opentelemetry-javaagent.jar

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

USER app

# ----------------------------------------------------------------------------
# Perfil "docker" — perfil único aceptado en la imagen de producción.
#
# El `-Dspring.profiles.active=docker` se pasa como SYSTEM PROPERTY en la JVM,
# que tiene MAYOR PRECEDENCIA que la env var SPRING_PROFILES_ACTIVE. Esto
# bloquea que un docker-compose env: o un .env accidentalmente active el
# perfil "demo" (carga datos dummy): el seeder está protegido por tres capas,
# ver DemoDataRunner @Profile("demo & !docker") + DemoGuard + este -D.
# ----------------------------------------------------------------------------

EXPOSE 8080

# Healthcheck robusto: actuator expone /actuator/health con disco/db
HEALTHCHECK --interval=15s --timeout=4s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# El profile "docker" va como -D, NO como ENV. -D pisa cualquier env var.
# OTel Java agent como -javaagent; configurable via env vars OTEL_*.
ENTRYPOINT ["java", \
  "-javaagent:/opt/opentelemetry-javaagent.jar", \
  "-Dspring.profiles.active=docker", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Duser.timezone=America/Mexico_City", \
  "-Djava.awt.headless=true", \
  "-jar", "/app/app.jar"]
