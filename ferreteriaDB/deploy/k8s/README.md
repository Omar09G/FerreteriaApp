# Despliegue en Kubernetes — Ferretería DB

## Aplicar todo

```bash
cd deploy/k8s

kubectl apply -f 00-base.yaml
kubectl apply -f 10-postgres.yaml
kubectl rollout status statefulset/postgres -n ferreteria --timeout=180s

# Cargar los scripts SQL como ConfigMap y ejecutar migración inicial
kubectl -n ferreteria create configmap ferreteria-sql --from-file=../../scripts/ --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f 40-migration-job.yaml
kubectl -n ferreteria logs job/ferreteria-db-init -f

kubectl apply -f 20-pgbouncer.yaml
kubectl apply -f 30-networkpolicy.yaml
kubectl apply -f 25-redis.yaml
kubectl apply -f 45-backend.yaml
kubectl apply -f 50-backend-hpa.yaml

# Probar conexión desde un pod temporal
kubectl -n ferreteria run psql-test --rm -it --image=postgres:17.11-alpine3.24@sha256:e5d2edd28b1f8d40a0a6c0c9cdc545b19b56a0d5d88f94bc11a97d970d4700d0 -- \
  psql -h pgbouncer -p 6432 -U ferreteria_app -d ferreteria \
  -c "SELECT * FROM ven.vw_resumen_dashboard;"
```

Antes de aplicar `45-backend.yaml`, sustituye los placeholders de
`ferreteria-backend-secret` con SealedSecrets/ExternalSecrets/Vault. Kubernetes
usa el adaptador S3-compatible (`STORAGE_PROVEEDOR=minio`), pero no despliega
Floci ni MinIO: `MINIO_ENDPOINT`, `MINIO_PUBLIC_URL`, `MINIO_ACCESS_KEY` y
`MINIO_SECRET_KEY` deben apuntar a un servicio de almacenamiento mantenido.

## Escalamiento

| Tipo | Dónde | Cómo |
|------|-------|------|
| **Vertical (PostgreSQL)** | `10-postgres.yaml` → resources + storage | sube cpu/memory limits y el PVC (storageClassName premium para NVMe) |
| **Horizontal (acceso)** | `20-pgbouncer.yaml` → HPA | 2–6 réplicas automáticas por CPU 70%; más réplicas = más clientes concurrentes |
| **Horizontal (lecturas)** | réplicas streaming | usar operador CloudNativePG (`cnpg`) para cluster HA con failover; apuntar reportes al servicio `-ro` |

## Producción

- Secrets: reemplazar por SealedSecrets/Vault.
- Backups: Velero sobre PVC o pgBackRest/CNPG scheduled backups.
- Monitoreo: exporter `prometheus-community/postgres-exporter` + alertas sobre `pg_stat_statements`.
- El job de migración es idempotente-safe: los scripts 03 usan ON CONFLICT; 05 es demo (no re-ejecutar).
