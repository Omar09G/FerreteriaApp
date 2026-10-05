# ferreteria-backend

API REST Spring Boot 3 / Java 21 sobre PostgreSQL (`../ferreteriaDB`).
Frontend: `../ferreteriaFront` · Index raíz: `../README.md`.

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

## Autenticación (dos fases + Google)

1. `POST /api/v1/auth/login {username,password}` → desafío OTP (canales +
   destinos enmascarados), sin tokens.
2. `POST /api/v1/auth/otp/enviar {challengeId,canal}` → código de 6 dígitos
   por email (HTML + texto) o WhatsApp. TTL 5 min, reenvío ≥60 s.
3. `POST /api/v1/auth/otp/verificar {challengeId,codigo}` → cookies `at`/`rt`.
   Máx 5 intentos; agotados/expirado → pedir nuevo desafío.
4. Google (redirect): `GET /api/v1/auth/oauth2/google` → callback →
   redirect a `/auth/callback?challengeId=` → mismo OTP. Vincula por email
   verificado o crea usuario `ENCARGADO_CAJA` (nunca `ADMIN`).

```bash
# Google OAuth (sin esto el botón Google responde OAUTH_FALLIDO)
export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...
export GOOGLE_REDIRECT_URI=http://localhost:8080/api/v1/auth/oauth2/google/callback
# OTP (defaults: 5 min / 5 intentos / reenvío 60 s)
export OTP_TTL_MINUTOS=5 OTP_MAX_INTENTOS=5 OTP_REENVIO_SEGUNDOS=60
```

En dev el OTP por WhatsApp cae a la bandeja mock en memoria y el email a
Mailpit (`MAIL_HOST:1025`); en prod configurar SMTP real y Evolution API
(`WHATSAPP_*` + `APP_NOTIF_ENABLED=true`).

## Recordatorio de cuentas por pagar (JOB 09:00 + botón manual)

Todos los días a las 09:00 (`CUENTAS_PAGAR_CRON`, zona `America/Mexico_City`)
se envía a GERENTES y ADMINISTRADORES un correo con las facturas vencidas
(prioridad de pago) y pendientes, con saldos y totales —mismos datos de
`GET /cuentas-pagar`, `/reportes/facturas-vencidas` y `/reportes/facturas-pendientes`.
Auditoría en `notif.notificacion_jobs` (tipo `CUENTAS_PAGAR`, un registro por día).

- Envío manual: botón "Enviar recordatorio" en Compras → Cuentas por pagar
  (`POST /api/v1/reportes/cuentas-pagar/informe`); antes pregunta
  (`GET .../estado`) si hoy ya se envió y pide confirmación para reenviar.
- Sin adeudos no se envía correo (se audita el job como ENVIADA).
- Requiere `APP_NOTIF_ENABLED=true`; el JOB se apaga con
  `CUENTAS_PAGAR_JOB_ENABLED=false`.
