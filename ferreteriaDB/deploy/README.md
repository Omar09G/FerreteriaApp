# Despliegue — Sistema Integral de Ferretería · PostgreSQL

Tres formas de desplegar la misma base, de menor a mayor escala:

| Opción | Uso | Escalamiento |
|--------|-----|--------------|
| **Podman Compose** (`docker-compose.yml`) | Desarrollo / servidor único | Vertical (límites CPU/RAM) + réplica de lectura opcional |
| **Kubernetes** (`k8s/`) | Clúster | Vertical (StatefulSet) + horizontal (HPA en PgBouncer) |
| **Terraform** (`terraform/`) | El clúster anterior, como código | Los mismos knobs como variables |

---

## 1) Podman Compose

```bash
cd deploy
cp .env.example .env        # EDITAR credenciales antes de producción
podman compose up -d        # o: podman-compose up -d

# Con réplica de lectura (streaming replication):
podman compose --profile replica up -d
```

Qué hace automáticamente en el primer arranque:
1. Crea rol `replicator` y aplica contraseñas desde `.env`.
2. Ejecuta `../scripts/01..05` en orden → esquemas, 70 tablas, vistas,
   parametría, usuario `admin` y datos demo.
3. Levanta PgBouncer con pool transaccional (1000 clientes / ~60 conexiones reales).

**La aplicación se conecta a:** `localhost:6432` · bd `ferreteria` · usuario `ferreteria_app`
(el primario directo queda en `5432` solo para administración; la réplica en `5433`).

```bash
# Verificación rápida
podman exec ferreteria-postgres-primary psql -U postgres -d ferreteria \
  -c "SELECT * FROM ven.vw_resumen_dashboard;"
```

Detalles probados que conviene conocer:
- El entrypoint oficial **ignora subdirectorios** en `initdb.d`: los mounts son archivos planos con prefijos de orden (`00_roles.sh`, `10_01_…sql`, …).
- `99_app_password.sh` corre al final porque el rol `ferreteria_app` nace dentro de `01_base_esquemas.sql`.
- La imagen oficial de PgBouncer tiene el binario en `/opt/pgbouncer/pgbouncer`; su entrypoint propio exige otras variables, por eso el servicio lo reemplaza (`entrypoint: []`) y renderiza `pgbouncer.ini` + `userlist.txt` desde `.env`.

---

## 2) Kubernetes

```bash
cd deploy/k8s
kubectl apply -f 00-base.yaml
kubectl apply -f 10-postgres.yaml
kubectl rollout status statefulset/postgres -n ferreteria --timeout=180s

# Scripts SQL como ConfigMap + migración inicial
kubectl -n ferreteria create configmap ferreteria-sql \
  --from-file=../../scripts/ --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f 40-migration-job.yaml && kubectl -n ferreteria logs job/ferreteria-db-init -f

kubectl apply -f 20-pgbouncer.yaml
kubectl apply -f 30-networkpolicy.yaml
```

- **Vertical**: resources/PVC del StatefulSet.
- **Horizontal**: HPA de PgBouncer 2→6 réplicas por CPU 70 % + PDB minAvailable=1.
- **Red**: NetworkPolicies zero-trust (solo PgBouncer → PostgreSQL).
- HA completa con failover: migrar al operador CloudNativePG manteniendo el mismo modelo SQL.

## 3) Terraform

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars   # editar credenciales
terraform init && terraform plan && terraform apply
```

Variables clave: `pg_cpu_limit` / `pg_memory_limit` / `storage_size` (vertical),
`pgbouncer_hpa_min/max/cpu_target` (horizontal), `load_demo_data=false` para producción.
Salidas: DSN JDBC vía PgBouncer listo para Spring Boot.

> Nota: Terraform requiere binario `terraform` (o OpenTofu ≥1.6) instalado; los manifiestos
> generados equivalen 1:1 a los YAML de `k8s/`.

---

## Producción (checklist)

- [ ] Cambiar TODAS las credenciales de `.env` / secrets (usar Vault/SealedSecrets)
- [ ] Fijar versiones de imagen por digest (`PGBOUNCER_IMAGE@sha256:…`)
- [ ] `load_demo_data=false` / omitir `05_dummy.sql`
- [ ] Backups: pgBackRest/WAL-G sobre el volumen + prueba de restauración
- [ ] Monitoreo: `postgres_exporter` + alertas sobre `pg_stat_statements`, replicación y autovacuum
- [ ] Réplicas de lectura para reportes si el POS crece a varias sucursales
