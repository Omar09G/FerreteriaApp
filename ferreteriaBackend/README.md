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

## Tiempo real (SSE) + bandeja + chat

- **Bandeja** (`notif.notificacion_bandeja`, V30): una fila por destinatario y
  evento, idempotente por `(usuario_id, tipo, ref_tipo, ref_id)`, retención 90
  días (purga 03:00 `America/Mexico_City`). Tipos: los 8 jobs + `VENTA_CANCELADA`,
  `COMPRA_CREADA`, `TURNO_APERTURA`, `CORTE_CAJA`, `NOMINA_CREADA`, `CHAT_MENSAJE`.
- **Eventos de dominio** (publican dentro de la tx, hook `AFTER_COMMIT` con
  `fallbackExecution` + `REQUIRES_NEW` en `RealtimeBandejaListener`): venta
  creada/cancelada (`ven`), compra creada (`com`), turno abierto/cerrado
  (`fin`), nómina creada/pagada/lote (`rh`). Destinatarios: GERENTES +
  ADMINISTRADORES (`InformeDestinatarioRepository.findGerenteAdminIds`) + el
  usuario propio (vendedor, cajero apertura/cierre, empleado de la nómina).
  Los 6 recordatorios publican a gerencia tras `marcarEnviada` (solo si hay
  registros).
- **SSE** (`RealtimePushService`, sin RabbitMQ): `GET /api/v1/notificaciones/stream`
  por usuario autenticado (el `usuarioId` sale del principal, nunca de params),
  latido cada 30 s, timeout 5 min (el front reconecta y reanuda desde el
  historial). El push se difiere a `AFTER_COMMIT`: un rollback nunca notifica.
  Endpoints: `GET /api/v1/notificaciones` (paginado `PageQuery`),
  `GET /no-leidas`, `PATCH /{id}/leida`, `PATCH /leidas` (todo `isAuthenticated()`).
  Con N réplicas el push solo llega a los conectados a la misma réplica
  (fase 2 = fanout por `ferreteria.events`).
- **Chat** (`mx.ferreteria.api.chat`, V31): directas idempotentes + grupos,
  pertenencia exigida en cada lectura (ajeno = 404), cuerpo 1–2000
  (`@Valid` + CHECK). Endpoints bajo `/api/v1/chat` (`isAuthenticated()`).
  Cada mensaje deja `CHAT_MENSAJE` en la bandeja de los demás participantes.
- **Decisión SSE vs WebSocket**: ver README raíz (sección Notificaciones).
  No añadir `spring-boot-starter-websocket` sin pasar por esa revisión.

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

## PDFs que viajan por correo (diseño legible)

- **Estilo único** (`common/pdf/PdfEstilo`, sin estado): paleta de marca
  (`#C2410C`, la misma del correo), tablas con encabezado oscuro y filas
  alternadas, moneda `es-MX` (`$1,234.56`), encabezado de marca + título +
  periodo, pie con `Página N` y metadata. Los 3 PDFs que van por correo lo
  usan; un cambio visual se hace una vez ahí.
- **Ticket** (`ven/pdf/TicketPdfService`): tabla `Cant. | Producto | P. unitario
  | Importe` con **nombre de producto** (resuelto por `ProductoRepository`,
  fallback `Producto #id`), importe por línea (`total_linea` de BD), ficha
  Folio/Fecha/Estado/Cliente y totales con `TOTAL` destacado. Formato A4 para
  correo (legible en pantalla, no 80 mm).
- **Informe diario** (`ven/pdf/DashboardInformePdfService`): KPIs en tabla
  `Indicador | Valor` (vencidas/agotados/cajas abiertas en rojo) y cierre en
  tabla `Fecha/Tickets/Total/Utilidad/Margen/Caja` con semáforo
  (`Cuadró` verde / `Diferencia $X` rojo).
- **Nómina** (`notif/service/NominaPdfService`): ficha
  Empleado/Periodo/Días/Estado/Fecha de pago + tabla
  Percepciones/Deducciones (− en rojo)/Neto destacado.
