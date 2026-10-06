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

## Recordatorios diarios (JOBs + botón manual)

| Recordatorio | Hora | Datos | Canales |
|---|---|---|---|
| Cuentas por pagar | 09:00 | facturas vencidas + pendientes (`GET /cuentas-pagar`, `/reportes/facturas-*`) | correo (detalle) + WhatsApp (resumen) |
| Cobranza | 09:05 | cuentas vencidas + pendientes (`GET /creditos/cobranza`) | correo (detalle) + WhatsApp (resumen) |
| Rentas | 09:10 | vencidas + próximas a devolver ≤3 días (`GET /rentas`) | correo (detalle) + WhatsApp (resumen) |
| Stock bajo | 09:15 | productos con stock ≤ mínimo (`GET /inventario?soloBajoStock=1`) | correo (resumen + Excel) + WhatsApp (totales) |
| Turnos abiertos | 21:00 | turnos sin cerrar del día | correo (detalle) + WhatsApp (resumen) |

Todos van a GERENTES y ADMINISTRADORES, con auditoría en
`notif.notificacion_jobs` (un registro por día por tipo) y la misma regla:
**solo se notifica si hay registros** (sin registros se audita sin enviar).

- Envío manual: botón "Enviar recordatorio"/"Avisar" en Compras → Cuentas por
  pagar, Ventas → Cobranza, Ventas → Rentas, Inventario → Existencias y Caja
  (`POST /api/v1/reportes/{cuentas-pagar,cobranza,rentas,stock-bajo,turnos}/informe`);
  antes pregunta (`GET .../estado`) si hoy ya se envió y pide confirmación
  para reenviar.
- Requiere `APP_NOTIF_ENABLED=true`; cada JOB se apaga con
  `CUENTAS_PAGAR_JOB_ENABLED` / `COBRANZA_JOB_ENABLED` / `RENTAS_JOB_ENABLED` /
  `STOCK_JOB_ENABLED` / `TURNO_JOB_ENABLED=false`.

## POS y ventas

- Búsqueda difusa (`GET /api/v1/productos/buscar?q=&limite=&almacenId=`):
  tolera typos con ranking (barras exacto > código > prefijo > substring >
  trigram), índice `GIN(lower(nombre))` (V27).
- Ticket por WhatsApp (`POST /api/v1/ventas/{id}/ticket-whatsapp {telefono}`):
  genera el PDF al momento y lo envía como documento (vía puerto
  `VentaTicketPort`, implementación en `notif` para no ciclar módulos).
- Narrativa del día (`GET /api/v1/reportes/narrativa?fecha=`): ventas hoy vs
  ayer (% con 1 decimal) + producto estrella por ingreso.
- Cotización con foto (`evidencia_url` en `ven.cotizaciones`, V29): URL de
  `/archivos/imagen` ligada al crear (misma regla que `foto_url`).
