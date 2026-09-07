# Staging Deploy — Ferretería

Validado 2026-09-07 — `docker compose config` OK (compose v5.5.1).

## Requisitos
- Docker daemon + compose v2
- `.env` en `ferreteriaDB/deploy/` (copiar de `.env.example` y ajustar secrets)
- Puertos libres: 5432 (postgres), 6432 (pgbouncer), 8080 (backend/nginx), 3001 (grafana), 4317/4318 (otel)

## Pasos
```bash
cp ferreteriaDB/deploy/.env.example ferreteriaDB/deploy/.env
# Ajustar POSTGRES_APP_PASSWORD, JWT_SECRET (openssl rand -base64 48), etc.

docker compose -f ferreteriaDB/deploy/docker-compose.yml up -d --build
docker compose -f ferreteriaDB/deploy/docker-compose.yml ps
docker compose -f ferreteriaDB/deploy/docker-compose.yml logs -f backend
```

## Smoke checks
```bash
curl -i http://localhost:8080/api/v1/auth/csrf-init  # 200 + Set-Cookie XSRF-TOKEN
curl -i http://localhost:8080/actuator/health         # 200 {"status":"UP"}
curl -i http://localhost:8080/v3/api-docs             # 200 OpenAPI JSON
curl -i http://localhost:4318/v1/traces -X POST -H "Content-Type: application/json" -d '{}'  # 400 (otel http up)
```

## Verificación audit
- `python3 scripts/validate.py audits/findings.yaml` → 0 errores
- `./gradlew test` → 567 tests 1 pre-existing
- `npx vitest run` → 16/16

## Notas
- Docker daemon no disponible en CI actual — config validado localmente, deploy real requiere host con daemon.
- K8s alternativa: `kubectl apply -f ferreteriaDB/deploy/k8s/` (00-base.yaml con REPLACE_ME secrets debe sobreescribirse via ExternalSecrets).
