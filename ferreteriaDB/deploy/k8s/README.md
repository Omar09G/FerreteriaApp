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

# Probar conexión desde un pod temporal
kubectl -n ferreteria run psql-test --rm -it --image=postgres:17-alpine -- \
  psql -h pgbouncer -p 6432 -U ferreteria_app -d ferreteria \
  -c "SELECT * FROM ven.vw_resumen_dashboard;"
```

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
