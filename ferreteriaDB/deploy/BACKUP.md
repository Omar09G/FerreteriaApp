# BACKUP — PITR con pgBackRest / WAL-G (DB-ESC-008)

> **PASO 31 · DB-ESC-008**: volumen `pg_data_primary` sin pgBackRest/WAL-G para PITR.
> Ya existe V1 con partitioning (`delta_partitioning_escalabilidad.sql`); este documento
> cubre el backup continuo y la capacidad de Point-in-Time Recovery sin modificar V1.

---

## 1. Objetivo

Garantizar **RPO ≈ 0** (pérdida máxima = último WAL archivado) y **RTO < 30 min**
mediante archivado continuo de WAL + backups base periódicos. Sin esto, la pérdida
del volumen `pg_data_primary` implica pérdida total desde el último `pg_dump`.

Alternativas equivalentes: **pgBackRest** (recomendado, documentado aquí) o **WAL-G**.
Ambos usan el mismo mecanismo de `archive_command` + `restore_command`.

---

## 2. Requisitos previos en `postgresql.conf`

Estos parámetros **deben estar activos** en `deploy/conf/postgresql.conf`:

```ini
wal_level = replica          # ya existe en postgresql.conf:36 — NO cambiar a minimal
archive_mode = on            # debe estar on para que archive_command se ejecute
archive_command = 'pgbackrest --stanza=ferreteria archive-push %p'
archive_timeout = 60s        # opcional: fuerza segmento WAL cada 60s aunque sin actividad
max_wal_senders = 5          # ya existe — necesario para base backup
wal_compression = on         # ya existe — reduce tamaño WAL archivado
```

> `wal_level = replica` ya está configurado en `postgresql.conf:40`. Si se cambia a
> `minimal` se rompe tanto la réplica streaming como PITR. `archive_mode` requiere
> reinicio (`SELECT pg_reload_conf()` no basta).

Verificación en runtime:

```sql
SHOW wal_level;    -- debe ser 'replica'
SHOW archive_mode; -- debe ser 'on'
SHOW archive_command;
SELECT * FROM pg_stat_archiver; -- verificar last_archived_wal sin failed_count
```

---

## 3. Configuración pgBackRest

### 3.1 `pgbackrest.conf`

Ubicación dentro del contenedor `postgres-primary`: `/etc/pgbackrest/pgbackrest.conf`
(Montar como volumen ro en `docker-compose.yml` si se usa imagen custom con pgBackRest).

```ini
[global]
repo1-path=/backups
repo1-retention-full=4
repo1-retention-diff=14
# Retención por tiempo (requiere pgBackRest >= 2.45 con repo1-retention-archive-type)
repo1-retention-archive-type=time
repo1-retention-archive=7
# Alternativa clásica por conteo si no se usa retención por tiempo:
# repo1-retention-full-type=count

# Compresión y checksums
compress-type=lz4
checksum-page-error=y

# Logging
log-level-console=info
log-level-file=detail

[ferreteria]
pg1-path=/var/lib/postgresql/data
pg1-port=5432
pg1-user=postgres
# pg1-host se omite en single-host; para repo remoto usar pg1-host=postgres-primary
```

Notas:

- `repo1-path=/backups` **debe ser un volumen persistente separado** de `pg_data_primary`
  (p.ej. `pgbackrest_repo:/backups` o mount NFS/S3). Si comparte volumen con PGDATA,
  la pérdida del disco pierde datos + backups.
- Stanza obligatoria: `ferreteria` (coincide con `PG_DATABASE`). No renombrar sin
  recrear stanza.
- Para S3/MinIO: añadir `repo1-type=s3`, `repo1-s3-bucket`, `repo1-s3-endpoint`, etc.

### 3.2 Inicializar stanza (una vez)

```bash
# Dentro del contenedor postgres-primary (o sidecar con acceso a PGDATA y /backups)
pgbackrest --stanza=ferreteria --log-level-console=info stanza-create

# Verificar archivado
pgbackrest --stanza=ferreteria check
psql -U postgres -c "SELECT pg_switch_wal();"  # fuerza archivado de un segmento
pgbackrest --stanza=ferreteria info
```

---

## 4. Backups

### 4.1 Backup completo

```bash
pgbackrest --stanza=ferreteria --type=full backup
```

- `--type=full`: copia completa de PGDATA + WAL necesario para consistencia.
- Alternativas incrementales: `--type=diff` (desde último full) o `--type=incr` (desde último backup).

### 4.2 Retención

| Nivel | Política | Parámetro |
|-------|----------|-----------|
| Diario | 7 días de WAL + diffs | `repo1-retention-archive=7` (time) / `repo1-retention-diff=14` |
| Semanal | 4 fulls semanales | `repo1-retention-full=4` |
| Mensual | 12 meses (conservar 1 full/mes) | `repo1-retention-full=12` si se quiere mensual puro, o archivar fulls mensuales a S3 Glacier |

> Política solicitada: **7 días / 4 semanas / 12 meses**. La configuración de ejemplo
> arriba cubre 7 días de WAL + 4 fulls semanales. Para 12 meses, promover a
> `repo1-retention-full=12` o mover fulls mensuales a storage frío fuera del repo
> local y documentar su ubicación.

Verificar expiración:

```bash
pgbackrest --stanza=ferreteria info --output=json | jq
# Los backups expirados se eliminan con:
pgbackrest --stanza=ferreteria expire
```

### 4.3 Cron — backup diario (ver también comentario en `docker-compose.yml`)

```cron
# /etc/cron.d/pgbackrest — ejecuta como usuario postgres
# Full semanal domingo 02:00, diff resto de días 02:00
0 2 * * 0 postgres pgbackrest --stanza=ferreteria --type=full backup >> /var/log/pgbackrest/backup.log 2>&1
0 2 * * 1-6 postgres pgbackrest --stanza=ferreteria --type=diff backup >> /var/log/pgbackrest/backup.log 2>&1
# Expiración diaria 03:00
0 3 * * * postgres pgbackrest --stanza=ferreteria expire >> /var/log/pgbackrest/expire.log 2>&1
```

En Docker/Podman sin cron del host, usar sidecar o `ofelia`/`supercronic`:

```yaml
# Ejemplo sidecar en docker-compose.yml (comentario de referencia)
# pgbackrest-cron:
#   image: postgres:17-alpine
#   volumes:
#     - pg_data_primary:/var/lib/postgresql/data:ro
#     - pgbackrest_repo:/backups
#     - ./conf/pgbackrest.conf:/etc/pgbackrest/pgbackrest.conf:ro
#   command: supercronic /etc/cron.d/pgbackrest
```

---

## 5. Restauración (PITR)

### 5.1 Restore completo (último backup)

```bash
# 1. Detener postgres-primary y vaciar PGDATA (¡destructivo!)
podman compose stop postgres-primary
podman volume rm ferreteria_pg_data_primary  # o vaciar: rm -rf /var/lib/postgresql/data/*

# 2. Restaurar
pgbackrest --stanza=ferreteria restore

# 3. Levantar
podman compose up -d postgres-primary
```

### 5.2 PITR a timestamp / LSN / nombre

```bash
# Restaurar a un punto en el tiempo (ej: antes de un DELETE erróneo)
pgbackrest --stanza=ferreteria --type=time "--target=2026-09-06 14:30:00 America/Mexico_City" \
  --target-action=promote restore

# Alternativas:
# --type=lsn  --target=0/50006D8
# --type=name --target=backup_label
# --type=xid  --target=12345
```

Requisitos para PITR:

- `recovery_target` + `recovery_target_action=promote` se escriben en `recovery.signal`
  (PG 12+) por pgBackRest automáticamente.
- El repo debe contener WAL continuo desde el backup base hasta el target. Si hay gap,
  PITR falla — de ahí la importancia de `archive_mode=on` + monitoreo de `pg_stat_archiver`.

### 5.3 Validación post-restore

```sql
SELECT pg_is_in_recovery(); -- false si ya promovió a primary
SELECT * FROM ven.vw_resumen_dashboard LIMIT 1;
-- Comparar conteos con el backup info:
-- pgbackrest --stanza=ferreteria info
```

---

## 6. WAL-G (alternativa)

Si se prefiere WAL-G (optimizado para S3):

```ini
# postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'wal-g wal-push %p'
```

```bash
wal-g backup-push /var/lib/postgresql/data
wal-g backup-fetch /var/lib/postgresql/data LATEST
```

La retención y PITR se gestionan con `WALG_DELTA_MAX_STEPS`, `WALG_RETENTION_COUNT`, etc.
El resto del flujo (repo separado, cron, verificación) es idéntico.

---

## 7. Monitoreo y alertas

```sql
-- WAL no archivado (crítico si failed_count > 0 o last_failed_wal no nulo)
SELECT * FROM pg_stat_archiver;

-- Tamaño del repo
-- pgbackrest --stanza=ferreteria info --output=json
```

Alertar si:

- `pg_stat_archiver.failed_count` incrementa.
- `pgbackrest check` falla.
- Espacio en `/backups` > 80%.
- Último backup full > 8 días sin éxito.

---

## 8. Checklist producción

- [ ] `repo1-path=/backups` en volumen/NFS/S3 separado de `pg_data_primary`
- [ ] `stanza-create` + `check` exitosos
- [ ] `archive_mode=on`, `wal_level=replica`, `archive_command` verificados con `SHOW`
- [ ] Cron de backup + expire probado (logs sin error)
- [ ] Restore de prueba en entorno staging (documentar fecha del último drill)
- [ ] Monitor `pg_stat_archiver` en `postgres-exporter` / Prometheus

---

## 9. Referencias

- pgBackRest: https://pgbackrest.org/configuration.html
- PostgreSQL PITR: https://www.postgresql.org/docs/current/continuous-archiving.html
- WAL-G: https://github.com/wal-g/wal-g
