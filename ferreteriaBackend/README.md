# ferreteria-backend

API REST Spring Boot 3 / Java 21 sobre PostgreSQL (`../ferreteriaDB`). Plan maestro:
`docs/PLAN_IMPLEMENTACION_BACKEND.md`.

## Requisitos

JDK 21 (toolchain), Docker o Podman para Testcontainers/integración (opcional en local:
los IT se saltan solos sin socket).

## Arranque rápido

```bash
./gradlew generateMigrations   # regenera V1/V2 desde ../ferreteriaDB/scripts si cambió el esquema
./gradlew bootRun              # usa PG_HOST/PG_PORT/PG_USER/PG_PASSWORD del entorno
```

Con el stack de BD de `../ferreteriaDB/deploy` levantado:

```bash
export PG_HOST=localhost PG_PORT=6432 PG_USER=ferreteria_app PG_PASSWORD=<ver deploy/.env>
./gradlew bootRun
# salud: http://localhost:8080/actuator/health
```

## Perfiles

| Perfil | Uso |
|---|---|
| *(default)* | Productivo: migraciones Flyway únicamente, SIN datos demo |
| `demo` | Desarrollo: además ejecuta `db/demo/05_dummy.sql` al arrancar |

## Comandos

```bash
./gradlew build          # compila + tests + gates JaCoCo (>=80% global, >=85% common/services)
./gradlew test           # unitarios
./gradlew generateMigrations
```

## Convenciones vivas

- Errores: RFC 7807 + `codigo` estable (`common/i18n/ErrorCode`) — mensajes SOLO en
  `resources/i18n/messages_{es,en}.properties`, nunca en código (ArchUnit lo vigila).
- Toda llamada lleva `X-Request-Id`: GENERATE (default) o STRICT vía env `REQUEST_ID_MODE`.
- ERRCODE P0xxx de la BD → ErrorCode → HTTP (PLAN §4.3).
