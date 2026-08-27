# ---------- Build (Gradle + JDK 21) ----------
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
# Cache de dependencias: primero solo descriptores
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle src/main/resources/db/migration /home/gradle/project/src/main/resources/db/migration
COPY --chown=gradle:gradle src/main/resources/i18n ./src/main/resources/i18n
RUN gradle dependencies --no-daemon || true
# Código completo (las migraciones ya están versionadas: la imagen construye sola)
COPY --chown=gradle:gradle src ./src
RUN gradle clean bootJar --no-daemon -x test

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Mexico_City"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
