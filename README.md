# Sistema Integral de Ferretería

Monorepo con tres proyectos que componen el sistema completo: base de datos, API REST y
aplicación web (SPA).

| Proyecto | Descripción | Stack |
|---|---|---|
| [`ferreteriaDB/`](ferreteriaDB/) | Modelo de datos y despliegue de la BD | PostgreSQL 14+, Podman Compose / Kubernetes / Terraform |
| [`ferreteriaBackend/`](ferreteriaBackend/) | API REST | Spring Boot 3, Java 21, Flyway, JUnit/JaCoCo |
| [`ferreteriaFront/`](ferreteriaFront/) | SPA de caja, inventario y reportes | React 19, Vite 8, TypeScript, TanStack Query, Tailwind |

## Arquitectura

- **Datos** — PostgreSQL con esquemas por módulo (`cat`, `cfg`, `rh`, `seg`, `inv`,
  `com`, `ven`, `fin`, `fis`), integridad vía triggers/funciones y zona
  `America/Mexico_City`. Los scripts de `ferreteriaDB/scripts` son la fuente de verdad;
  el backend los consolida en migraciones Flyway.
- **Backend** — API REST en `http://localhost:8080` (`/api/v1`), autenticación JWT con
  refresh rotativo y sesión única, errores RFC 7807 con códigos estables y mensajes
  i18n. Tests unitarios + gates de cobertura JaCoCo (≥80% global).
- **Frontend** — SPA servida por Vite (dev `http://localhost:5173` con proxy `/api →
  :8080`; prod `VITE_API_URL`). Autenticación, roles, POS, caja/cortes, inventario,
  compras, reportes y dashboard.

## Quickstart (todo el stack)

```bash
# 1) Base de datos (PostgreSQL + PgBouncer)
cd ferreteriaDB/deploy && cp .env.example .env && podman compose up -d

# 2) Backend (aplica migraciones al arrancar; Gradle carga `.env` solo con JWT_SECRET, obligatorio sin default)
cd ../../ferreteriaBackend
./gradlew bootRun

# 3) Frontend
cd ../ferreteriaFront && bun install && bun run dev
```

Detalles y comandos de pruebas/build en el README de cada proyecto.

## Puertos y conexiones (dev local)

La app corre con `bootRun` + `bun run dev` en el host; solo datos, storage y
observabilidad van en contenedores (`ferreteriaDB/deploy`, red `db-net`/`app-net`/`obs-net`).

| Servicio | Contenedor / proceso | Puerto host | URL / uso |
|---|---|---|---|
| Frontend Vite | host (`bun run dev`) | 5173 | http://localhost:5173 · proxy `/api → :8080` |
| Backend API | host (`./gradlew bootRun`) | 8080 | http://localhost:8080/`api/v1` · `/actuator/health` |
| PostgreSQL primario | `ferreteria-postgres-primary` | 5432 | admin directo (la app usa PgBouncer) |
| PostgreSQL réplica | `ferreteria-postgres-replica` | 5433 | solo lectura |
| PgBouncer | `ferreteria-pgbouncer` | 6432 | conexión de la app (`PG_HOST/PORT`) |
| MinIO API (fotos) | `ferreteria-minio` | 9000 | S3 + URLs públicas `http://localhost:9000/ferreteria-fotos/…` |
| MinIO consola | `ferreteria-minio` | 9001 | http://localhost:9001 (usuario `MINIO_ROOT_USER`) |
| Floci S3 (fotos) | `ferreteria-floci` | 4566 | S3 local; backend lo usa con `STORAGE_PROVEEDOR=floci` (default: `minio`) |
| Floci consola web | sidecar `floci-ui` | 4500 | http://localhost:4500 (o vía http://localhost:4566/_floci/ui); requiere socket del motor (ver `PODMAN_SOCKET`) |
| Backend contenerizado (opcional) | `ferreteria-backend` | 8081 | swagger/health directo; dentro de compose usa `MINIO_ENDPOINT=http://minio:9000` |
| Frontend contenerizado (opcional) | `ferreteria-frontend` | 8080 | Nginx `:80`; solo prod/staging (choca con bootRun) |

Notas:
- Proveedor de fotos (`STORAGE_PROVEEDOR=minio|floci`, default `minio`):
  MinIO (`minio:9000` interno, `MINIO_PUBLIC_URL` al browser) o Floci S3
  (`floci:4566` interno, `FLOCI_PUBLIC_URL` al browser). En ambos hay dos
  URLs distintas con propósito distinto: **ENDPOINT** = lo que usa el backend
  en red interna para subir; **PUBLIC_URL** = base de la URL que se guarda en
  `foto_url`/`imagen_url` y resuelve el browser (debe ser alcanzable desde
  quien use la app, no necesariamente localhost).
- `bootRun` en host usa `MINIO_ENDPOINT=http://localhost:9000` (el DNS `minio`
  solo existe dentro de la red compose). `MINIO_PUBLIC_URL` debe ser alcanzable
  desde el browser (dev: `http://localhost:9000`). Con Floci es igual:
  `FLOCI_ENDPOINT=http://localhost:4566` en host, `http://floci:4566` en compose.
- Subir fotos: `POST /api/v1/archivos/imagen` (multipart `archivo`,
  jpeg/png/webp ≤5 MB, requiere rol operativo + header `X-XSRF-TOKEN`) →
  `201 { data: { url } }` → esa URL pública va en `fotoUrl`/`imagenUrl` del
  create/update. El backend optimiza a JPEG, renombra a UUID y crea el bucket
  público solo si no existe. Con Floci la consola muestra los objetos en
  http://localhost:4500.
- La imagen de subida requiere imagen `quay.io/minio/minio` (Docker Hub
  rechaza el pull del tag fijado en el compose).

## Observabilidad (OTel + Prometheus)

| Servicio | Contenedor | Puerto host | Uso |
|---|---|---|---|
| OTel Collector | `ferreteria-otel-collector` | 4317 gRPC / 4318 HTTP / 8889 prom | OTLP del backend (4317) y del browser (4318, con CORS); `VITE_OTEL_ENABLED=false` en dev local |
| Tempo | `ferreteria-tempo` | 3200 | trazas (datasource de Grafana) |
| Prometheus | `ferreteria-prometheus` | 9090 | métricas (scrapea collector :8889 y postgres-exporter :9187) |
| Postgres exporter | `ferreteria-postgres-exporter` | 9187 | métricas de PG |
| Grafana | `ferreteria-grafana` | 3000 | dashboards (Tempo + Prometheus) |

## Scripts de soporte

- `collector/` — colección de requests HTTP de apoyo (collections para probar la API).


DoD global: `./gradlew build` (JaCoCo ≥80%), `bun test` en verde, validadores en
PASS y cada historia con test que falla antes y pasa después + replay en staging.
