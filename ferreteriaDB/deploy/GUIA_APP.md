# Guía de Levantamiento — Ferretería "El Tornillo Feliz" (Stack Completo)

> Levanta **toda** la aplicación: frontend (React), backend (Spring Boot), base de
> datos (PostgreSQL 17 + PgBouncer) y observabilidad (OTel, Prometheus, Grafana).
> Todo orquestado con `docker-compose.yml` bajo **podman**.
>
> Complementa a `GUIA_PODMAN.md` (que cubre solo la BD en profundidad). Esta guía
> es la referencia para arrancar y operar **el sistema completo**.

---

## Índice

1. [Arquitectura y servicios](#1-arquitectura-y-servicios)
2. [Requisitos](#2-requisitos)
3. [Configuración inicial (`.env`)](#3-configuración-inicial-env)
4. [Construcción de imágenes](#4-construcción-de-imágenes)
5. [Arranque paso a paso](#5-arranque-paso-a-paso)
6. [Puertos y endpoints](#6-puertos-y-endpoints)
7. [Credenciales de conexión](#7-credenciales-de-conexión)
8. [Verificación end-to-end](#8-verificación-end-to-end)
9. [Operación diaria](#9-operación-diaria)
10. [Solución de problemas conocidos](#10-solución-de-problemas-conocidos)
11. [Checklist de producción](#11-checklist-de-producción)

---

## 1. Arquitectura y servicios

```
host              red app-net                          red db-net            red obs-net
┌──────────┐   ┌────────────┐   ┌──────────────┐   ┌────────────┐   ┌─────────────┐
│ Browser  │──►│  frontend  │──►│   backend    │──►│  pgbouncer │──►│postgres-17  │
│ :8080    │   │  nginx :80 │   │  spring :8080│   │ (pool) 6432│   │ (primario)  │
└────┬─────┘   └────┬───────┘   └──────┬───────┘   └────────────┘   └──┬──┬──────┘
     │ OTLP HTTP    │ OTLP gRPC        │               │metrics 9187     │
     │ 4318 (browser) 4317             ▼               ▼  ┌──────────────┘
     └──────────────► ┌────────────┐  ┌────────────┐  ┌──────────────┐
                      │ OTEL       │  │ postgres-  │  │              │
                      │ collector  │──►│ exporter   │  │              │
                      │ :4317/4318 │  │ (9187)     │  │              │
                      └────┬───────┘  └────────────┘  └──────────────┘
                           │ OTLP grpc (traces)
                           ▼
                      ┌────────────┐   scrape   ┌────────────┐
                      │   Tempo    │◄──────────│ Prometheus │──► Grafana :3000
                      │  traces    │            │  :9090     │
                      └────────────┘            └────────────┘
```

| Servicio | Imagen | Rol |
|---|---|---|
| `frontend` | `ferreteria/frontend` (nginx) | SPA React, proxy `/api` → backend |
| `backend` | `ferreteria/backend` (Java 21, OTel agent) | API REST Spring Boot 3.3 |
| `postgres-primary` | `postgres:17-alpine` | Base de datos (escrituras) |
| `pgbouncer` | `pgbouncer/pgbouncer` | Pool de conexiones (transaction mode) |
| `otel-collector` | `otel/opentelemetry-collector-contrib:0.111.0` | Recepción OTLP → Prometheus + Tempo |
| `tempo` | `grafana/tempo:2.6.1` | Backend de trazas (backend + browser) |
| `postgres-exporter` | `prometheuscommunity/postgres-exporter:v0.16.0` | Métricas nativas de Postgres |
| `prometheus` | `prom/prometheus:v2.55.0` | Time-series + alertas |
| `grafana` | `grafana/grafana:11.3.0` | Dashboards + trazas (provisionados) |

Rutas de datos: `frontend → backend → pgbouncer → postgres`.
Observabilidad: `backend/browser → otel-collector → prometheus → grafana` (métricas) y `otel-collector → tempo → grafana` (trazas); `postgres-exporter → prometheus`.

---

## 2. Requisitos

| Componente | Versión | Verificar |
|---|---|---|
| Podman | 5.x (probado 5.4.2) | `podman --version` |
| podman-compose **o** `podman compose` | 1.3+ | `podman-compose --version` |
| RAM | ≥ 4 GB (8 GB recomendado c/ observabilidad) | `free -h` |
| Disco | ≥ 6 GB (imágenes + datos demo) | `df -h` |
| Puertos libres | 8080, 8081, 5432, 6432, 4317, 4318, 8889, 9187, 9090, 3000 | `ss -ltn` |

> **Docker Desktop / credencial helper:** si `podman compose pull` falla con
> `docker-credential-desktop: executable file not found`, las imágenes locales ya
> existen; simplemente usa `podman compose up -d` (no fuerce a re-pull), o baja
> las imágenes con `podman pull` antes.

---

## 3. Configuración inicial (`.env`)

El archivo `.env` **en este directorio** es la fuente única de credenciales en
runtime (Postgres, PgBouncer, backend, Grafana).

```bash
cd ferreteriaDB/deploy
cp .env.example .env
nano .env
```

Variables **obligatorias** (el compose aborta si faltan):

```bash
POSTGRES_ADMIN_PASSWORD=cambia_admin_seguro   # rol admin postgres
POSTGRES_APP_PASSWORD=cambia_app_seguro       # rol ferreteria_app
REPLICATION_PASSWORD=cambia_replica_seguro
PG_PASSWORD=cambia_app_seguro                 # backend usa esta (misma que app)
JWT_SECRET=<base64 ≥32 bytes>                 # firma de tokens
CORS_ALLOWED_ORIGINS=http://localhost:8080,http://localhost:5173,http://localhost:4200
GRAFANA_ADMIN_PASSWORD=cambia_grafana
```

Observabilidad (opcional, tienen valores por defecto):

```bash
OTEL_SDK_DISABLED=false                       # true = apaga telemetría backend
VITE_OTEL_ENABLED=true                        # telemetría del browser
VITE_OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
OTEL_COLLECTOR_IMAGE=otel/opentelemetry-collector-contrib:0.111.0
TEMPO_IMAGE=grafana/tempo:2.6.1
PROMETHEUS_IMAGE=prom/prometheus:v2.55.0
GRAFANA_IMAGE=grafana/grafana:11.3.0
POSTGRES_EXPORTER_IMAGE=prometheuscommunity/postgres-exporter:v0.16.0
```

> El **frontend** lee sus variables `VITE_*` en **build time** (quedan embebidas
> en el bundle). El backend lee las suyas en **runtime**. Por eso arranques con
> `--build` necesitan que `VITE_OTEL_ENABLED` y el endpoint estén bien definidos.

---

## 4. Construcción de imágenes

Se construyen desde sus respectivas carpetas vía el propio compose (contexts):

| Imagen | Contexto (build) |
|---|---|
| `ferreteria/frontend:latest` | `../ferreteriaFront` (Dockerfile multi-stage bun+nginx) |
| `ferreteria/backend:latest` | `../ferreteriaBackend` (Dockerfile con OTel agent) |

```bash
cd ferreteriaDB/deploy
# Construir ambas imágenes sin arrancar
podman compose build
# o píe por píe
podman compose build backend frontend
```

Verificar lo construido:

```bash
podman images | grep ferreteria
podman inspect ferreteria/backend:latest --format '{{range .Config.Entrypoint}}{{println .}}{{end}}'
# debe incluir: -javaagent:/opt/opentelemetry-javaagent.jar
```

---

## 5. Arranque paso a paso

```bash
cd ferreteriaDB/deploy

# 1) Validar el archivo (sin levantar nada)
podman compose config

# 2) Levantar todo (construye solo si faltan imágenes)
podman compose up -d --build

# 3) Estado (esperar a que todo marque "healthy")
podman compose ps
```

Los healthchecks desbloquean la cadena de arranque en orden:
`postgres-primary` → `pgbouncer` → `backend` → `frontend`; en paralelo
`otel-collector` → `tempo` → `prometheus` → `grafana` (con el `postgres-exporter`).

Primer arranque (solo la primera vez):
1. `postgres:17` crea roles y la BD `ferreteria` desde los scripts de `conf/init/`.
2. El esquema, ~70 tablas, vistas, usuario `admin` y datos demo se cargan.

> Apagar / reiniciar:
> ```bash
> podman compose down            # detiene y elimina contenedores (conserva datos)
> podman compose down -v         # + elimina volúmenes (BORRA los datos)
> podman compose restart backend # reinicia solo un servicio
> ```

---

## 6. Puertos y endpoints

| Puerto host | Puerto ctr | Servicio | Uso |
|---|---|---|---|
| `8080` | 80 | frontend | **SPA** (interfaz web) |
| `8081` | 8080 | backend | API directa + actuator |
| `5432` | 5432 | postgres-primary | BD directa (solo admin) |
| `6432` | 6432 | pgbouncer | Conexión BD vía pool (la app) |
| `4317` | 4317 | otel-collector | OTLP **gRPC** (backend → telemetría) |
| `4318` | 4318 | otel-collector | OTLP **HTTP** (browser → telemetría, CORS) |
| `8889` | 8889 | otel-collector | `/metrics` Prometheus del collector |
| `9187` | 9187 | postgres-exporter | `/metrics` del exporter |
| `9090` | 9090 | prometheus | UI Prometheus + API de consulta |
| `3200` | 3200 | tempo | API de consulta de trazas (datasource Grafana) |
| `3000` | 3000 | grafana | Dashboards + trazas |

URLs útiles:

```text
App web        http://localhost:8080
Backend salud  http://localhost:8081/actuator/health
Prometheus     http://localhost:9090
Grafana        http://localhost:3000     (admin / cambia_grafana)
Tempo          http://localhost:3200     (buscar trazas: /api/search)
```

---

## 7. Credenciales de conexión

### Base de datos (PostgreSQL 17 / PgBouncer)

| Rol | Usuario | Password | Uso |
|---|---|---|---|
| Admin | `postgres` | `cambia_admin_seguro` | DBA, migraciones, exporter |
| Aplicación | `ferreteria_app` | `cambia_app_seguro` | Backend / app |
| Replication | `replicator` | `cambia_replica_seguro` | Réplicas físicas |

```bash
# Directo al primario (5432)
psql postgresql://postgres:cambia_admin_seguro@localhost:5432/ferreteria
psql postgresql://ferreteria_app:cambia_app_seguro@localhost:5432/ferreteria

# Vía PgBouncer (6432) — la que usa la app
psql postgresql://ferreteria_app:cambia_app_seguro@localhost:6432/ferreteria
```

### Backend (usa PgBouncer)

```env
PG_HOST=pgbouncer · PG_PORT=6432 · PG_DATABASE=ferreteria
PG_USER=ferreteria_app · PG_PASSWORD=cambia_app_seguro
```

### Grafana

| Usuario | Password |
|---|---|
| `admin` | `cambia_grafana` |

### Auth de la aplicación (JWT)

`JWT_SECRET` firma los tokens; la app usa cookies HttpOnly (`at`, `rt`) para autenticar.

> Todas las contraseñas listadas son **placeholders de dev**. Cambiarlas antes de
> producción (ver §11).

---

## 8. Verificación end-to-end

```bash
# 1) Todos los servicios con healthcheck marcan "healthy"
podman compose ps

# 2) Backend sano
curl -s http://localhost:8081/actuator/health
#   → {"status":"UP","groups":["liveness","readiness"]}

# 3) Frontend responde
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/
#   → 200

# 4) Métricas de la app (Micrometer + OTel) expuestas
curl -s http://localhost:8081/actuator/prometheus | head
#   → series jvm_*, http_server_requests_*, etc.

# 5) Prometheus ve todos los targets
curl -s 'http://localhost:9090/api/v1/targets' | grep -o '"health":"[a-z]*"' | sort | uniq -c
#   → 4 "up"  (prometheus, otel-collector, postgres-exporter, backend-spring)

# 6) Métricas de Postgres (incluida la de PG17) llegan a Prometheus
curl -s 'http://localhost:9090/api/v1/query?query=pg_stat_checkpointer_num_timed'
#   → status success (PG17: checkpoints)
curl -s 'http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes'

# 7) Colección de telemetría acepta trazas (browser/backend)
curl -s -o /dev/null -w '%{http_code}\n' -H 'content-type: application/json' \
  -X POST http://localhost:4318/v1/traces -d '{"resourceSpans":[]}'
#   → 200

# 8) Grafana listo
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/api/health
#   → 200

# 9) Trazas end-to-end: generar tráfico y confirmar que llegan a Tempo
NOw=$(date +%s)
for i in 1 2 3; do curl -s -o /dev/null http://localhost:8081/actuator/health; sleep 1; done
curl -s "http://localhost:3200/api/search?limit=10&start=$((NOw-300))&end=$NOw"
#   → {"traces":[{"traceID":"…","rootServiceName":"ferreteria-backend",…}]}
#     (backend y/o ferreteria-frontend si el browser navegó)

# 10) Frontend metrics ya prefijadas por el collector (nombre real ferreteria_*):
curl -s 'http://localhost:9090/api/v1/query?query=ferreteria_frontend_web_vitals_lcp_seconds_count'
#   → 1 (el browser envió LCP; será 0 hasta que alguien abra la app)
```

---

## 9. Operación diaria

```bash
# Estado en vivo (con salud)
watch -n 2 'podman compose ps'

# Logs de un servicio / de todos
podman compose logs -f backend
podman compose logs -f --tail=100

# Reiniciar un servicio
podman compose restart pgbouncer

# Escalar réplica de lectura (perfil opcional)
podman compose --profile replica up -d

# Volúmenes de datos (persisten entre reinicios)
podman volume ls | grep ferreteria
```

Dónde ver problemas en caliente:

| Síntoma | Servicio | Log |
|---|---|---|
| El backend no conecta a la BD | backend | `podman logs ferreteria-backend` |
| Pool lento / sin conexiones | pgbouncer | `podman logs ferreteria-pgbouncer` |
| Métricas no llegan | postgres-exporter / otel | `podman logs ferreteria-postgres-exporter` / `ferreteria-otel-collector` |

---

## 10. Solución de problemas conocidos

> Provienen del arranque real validado de esta stack con podman 5.4.

**1. `You have to set the DATABASES_HOST or DATABASES Environment variable` (pgbouncer)**
El proveedor compose externo **ignora `entrypoint: []`**. La imagen de PgBouncer
corre su entrypoint propio que exige `DATABASES_HOST`.
→ Fijado con `entrypoint: ["/bin/sh","-ecx"]` y el script de config en `command`.
No re-introduzcas `entrypoint: []`.

**2. `column "checkpoints_timed" does not exist` (postgres-exporter)**
El collector `stat_bgwriter` integrado usa el esquema de Postgres **≤16**. En
Postgres **17** los contadores de checkpoint viven en `pg_stat_checkpointer`.
→ Se desactiva con `--no-collector.stat_bgwriter` y se definen las métricas PG17
en `conf/postgres/queries.yaml` vía `--extend.query-path`.

**3. El otel-collector nunca marca "healthy"**
La imagen `opentelemetry-collector-contrib` es **distroless** (solo
`/otelcol-contrib`, sin `wget`/`curl`/shell), por eso no puede ejecutar un
healthcheck HTTP interno.
→ Se eliminó su healthcheck; `prometheus` espera solo `service_started`
(no `service_healthy`). La disponibilidad del collector no debe bloquear la app.

**4. Falta `psql` en la imagen de PgBouncer**
La imagen es Alpine y no trae el cliente. No uses `psql` en el healthcheck.
→ El healthcheck usa `nc -z 127.0.0.1 6432` (netcat de busybox).

**5. `docker-credential-desktop` no encontrado al hacer pull**
Recoño de Docker Desktop en `~/.docker/config.json`. Las imágenes necesarias ya
están locales.
→ Usa `podman compose up -d` directamente o `podman pull` explícito por imagen.

**6. "No data" en paneles de métricas del frontend en Grafana**
Los nombres reales en Prometheus llevan prefijo `ferreteria_` (el collector los
renombra con `namespace: ferreteria`). El dashboard ahora consulta
`ferreteria_frontend_*`. Si algún panel sigue vacío:
```bash
curl -s 'http://localhost:9090/api/v1/query?query={__name__=~"ferreteria_frontend.*"}'
```
Los Web Vitals (LCP/FCP/TTFB/navegación) solo aparecen **después** de que un
browser cargue la app; y `ferreteria_frontend_js_errors_total` **solo existe
tras el primer error JS real** (es un contador perezoso — vacío = sin errores,
correcto).

**7. No aparecen trazas en Grafana**
Las trazas sí se reciben (backend vía gRPC 4317, browser vía HTTP 4318) y el
collector las reenvía a **Tempo** (`otlp` exporter). Verificables en:
```bash
# collector debe listar el exporter otlp + debug en la pipeline de trazas
podman logs -f ferreteria-otel-collector | grep -i otlp
# Tempo debe tener datos (p.ej. GET /actuator/health)
NOw=$(date +%s); curl -s "http://localhost:3200/api/search?limit=10&start=$((NOw-600))&end=$NOw"
```
En Grafana, ve a **Explore → Tempo** (o el panel "Traces") para verlos. Si algo
cambia en `otel-collector.yaml` o `tempo.yaml`, reinicia ambos: `podman compose
restart otel-collector tempo`.

**8. Reconectar tras errores de arranque a medias**
Los `compose up` abortados pueden dejar contenedores "Created". Reconciliar:
```bash
podman compose down -v
# (es decir: si un contenedor queda colgado)
podman rm -f $(podman ps -a --format '{{.Names}}' | grep ferreteria)
```
Luego `podman compose up -d --build` de nuevo.

---

## 11. Checklist de producción

- [ ] Cambiar **todas** las credenciales de `.env`: `POSTGRES_ADMIN_PASSWORD`,
      `POSTGRES_APP_PASSWORD`, `REPLICATION_PASSWORD`, `PG_PASSWORD`,
      `JWT_SECRET`, `GRAFANA_ADMIN_PASSWORD`.
- [ ] Fijar imágenes por digest (`@sha256:…`) en `.env` (`*_IMAGE`).
- [ ] `AUTH_COOKIE_SECURE=true` cuando sirvan por HTTPS.
- [ ] Revisar `CORS_ALLOWED_ORIGINS` (solo orígenes reales).
- [ ] Desactivar datos demo en producción (omitir el script de demo).
- [ ] Backups: pgBackRest/WAL-G sobre el volumen + prueba de restauración.
- [ ] Alertas sobre `pg_stat_statements`, replicación y autovacuum en Prometheus.
- [ ] Tempo: para producción apuntar `storage.trace.backend` a S3/GCS/Azure
      (`conf/otel/tempo.yaml`); en dev queda en disco (`/tmp/tempo`).
- [ ] `OTEL_SDK_DISABLED` y `VITE_OTEL_ENABLED` según si se quiere telemetría.

---