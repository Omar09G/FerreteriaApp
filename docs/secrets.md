# Secrets Management — Ferretería (prod)

Este doc describe cómo gestionar secretos fuera del repo (BACK-SEC-004, DB-SEC-013).

## Principio

Ningún secreto va commiteado. `.env.example` tiene placeholders vacíos.
El operador los inyecta via:

- **Docker Compose (dev/staging):** `.env` (gitignored, creado desde `.env.example`)
- **Kubernetes (prod):** `ExternalSecrets` / `SealedSecrets` / `Vault`

## Kubernetes (recomendado para prod)

### External Secrets Operator (AWS/GCP/Azure)

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: ferreteria-secrets
  namespace: ferreteria
spec:
  secretStoreRef: { name: vault-backend, kind: SecretStore }
  target: { name: ferreteria-db-secret, creationPolicy: Owner }
  data:
    - secretKey: POSTGRES_APP_PASSWORD
      remoteRef: { key: ferreteria/prod, property: pg_app_password }
    - secretKey: JWT_SECRET
      remoteRef: { key: ferreteria/prod, property: jwt_secret }
```

### SealedSecrets (alternativa sin Vault)

```bash
kubectl create secret generic ferreteria-db-secret \
  --from-literal=POSTGRES_APP_PASSWORD="$(openssl rand -base64 24)" \
  --dry-run=client -o yaml | kubeseal --format yaml > k8s/secrets.yaml
```

## Rotación JWT_SECRET (BACK-SEC-033)

1. Genera nuevo: `openssl rand -base64 48` → `NEW`
2. Setea `JWT_PREVIOUS_SECRET=<OLD>` + `JWT_SECRET=<NEW>` en todos los pods
3. Rolling restart: `kubectl rollout restart deployment/ferreteria-backend`
4. Tras 1 ventana (8h, TTL del refresh), quita `JWT_PREVIOUS_SECRET`

## Verificación

```bash
# Backend debe fallar rápido si JWT_SECRET falta o <32 bytes
JWT_SECRET="" ./gradlew bootRun  # debe lanzar IllegalStateException
# Health debe seguir sin auth
curl -i http://localhost:8080/actuator/health  # 200
# Prometheus debe requerir auth
curl -i http://localhost:8080/actuator/prometheus  # 401
```
