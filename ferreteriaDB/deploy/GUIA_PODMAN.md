# Guía Completa de Ejecución con Podman — Base de Datos Ferretería

> Sistema Integral de Ferretería · PostgreSQL 17 + PgBouncer (+ réplica opcional)
> Zona horaria fija: **America/Mexico_City** · Todo validado con **podman 5.x** y `podman-compose 1.3`

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Estructura de archivos](#2-estructura-de-archivos)
3. [Configuración `.env` (variable por variable)](#3-configuración-env)
4. [Configuración de PostgreSQL (`postgresql.conf`)](#4-configuración-de-postgresql)
5. [Seguridad de acceso (`pg_hba.conf` y roles)](#5-seguridad-de-acceso)
6. [Arranque paso a paso](#6-arranque-paso-a-paso)
7. [Conexiones (POS / backend / administración)](#7-conexiones)
8. [Verificación del sistema](#8-verificación-del-sistema)
9. [Operación diaria](#9-operación-diaria)
10. [Réplica de lectura](#10-réplica-de-lectura)
11. [Autoarranque al boot con systemd (Quadlet)](#11-autoarranque-al-boot-con-systemd)
12. [Solución de problemas conocidos](#12-solución-de-problemas-conocidos)
13. [Guía de escalamiento vertical (perfiles por hardware)](#13-guía-de-escalamiento-vertical)
14. [Checklist de producción](#14-checklist-de-producción)

---

## 1. Requisitos previos

| Componente | Versión mínima | Verificar |
|---|---|---|
| Podman | 4.5+ (probado en 5.4) | `podman --version` |
| podman-compose **o** `podman compose` | 1.0.5+ | `podman-compose --version` |
| RAM disponible para la BD | 2 GB (mín.) · 4 GB (recomendado) | `free -h` |
| Disco | 5 GB libres (datos demo) · SSD/NVMe recomendado | `df -h` |
| Puertos libres | **5432** (primario), **6432** (pool), **5433** (réplica) | `ss -ltn \| grep 543` |

Instalación rápida de podman-compose si falta:

```bash
pip3 install --user podman-compose        # o: sudo dnf install podman-compose
export PATH="$HOME/.local/bin:$PATH"
```

---

## 2. Estructura de archivos

```
ferreteriaDB/
├── scripts/                      ← Scripts SQL fuente (01..05 + vistas_core.sql)
└── deploy/
    ├── docker-compose.yml        ← Definición de servicios (primary, réplica, pool)
    ├── .env                      ← CREDENCIALES Y RECURSOS (no subir a git)
    ├── .env.example              ← Plantilla para crear .env
    ├── README.md                 ← Resumen rápido (incluye k8s/Terraform)
    ├── conf/
    │   ├── postgresql.conf       ← Tuning de rendimiento (sección 4)
    │   ├── pg_hba.conf           ← Reglas de acceso (sección 5)
    │   ├── init/
    │   │   ├── 00_replica_user.sh    ← Crea rol 'replicator' (antes de los .sql)
    │   │   └── 99_app_password.sh    ← Aplica password de app (después de los .sql)
    │   └── replica/
    │       └── bootstrap-replica.sh  ← Bootstrap de la réplica (pg_basebackup)
    └── k8s/ · terraform/         ← Despliegue alternativo en clúster (ver deploy/README.md)
```

**Orden de inicialización automática** (primer arranque, volumen vacío):
`00_replica_user.sh` → `01_base_esquemas.sql` → `02_tablas.sql` → `vistas_core.sql`
→ `03_parametria.sql` → `04_admin.sql` → `05_dummy.sql` → `99_app_password.sh`.

> Los montajes son **archivos planos con prefijo numérico** porque el entrypoint oficial
> de PostgreSQL ignora subdirectorios dentro de `/docker-entrypoint-initdb.d`.

---

## 3. Configuración `.env`

```bash
cd ferreteriaDB/deploy
cp .env.example .env
nano .env
```

| Variable | Obligatoria | Función |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD` | Sí | Password del superusuario `postgres` (job de migración, mantenimiento) |
| `POSTGRES_APP_PASSWORD`   | Sí | Password del rol `ferreteria_app` — el que usa el POS/backend vía PgBouncer |
| `REPLICATION_PASSWORD`    | Solo con réplica | Password del rol `replicator` (streaming) |
| `PG_IMAGE`                | No | Imagen PostgreSQL (`postgres:17-alpine`) — fijar por digest en prod |
| `PGBOUNCER_IMAGE`         | No | `pgbouncer/pgbouncer:latest` — fijar tag/digest en prod |
| `PG_MEM_LIMIT`            | No | Límite de RAM del contenedor (default `2g`) |
| `PG_CPUS`                 | No | CPUs asignadas (default `2.0`) |
| `PG_SHM_SIZE`             | No | Memoria compartida para paralelismo (default `256mb`) |
| `PRIMARY_HOST`            | No | Hostname del primario visto por la réplica (`postgres-primary`) |

Reglas:
- **Nunca hacer commit de `.env`** (añádelo a `.gitignore`).
- Si cambias contraseñas DESPUÉS del primer arranque: además de editar `.env`, aplica el cambio
  en la BD (sección 9.4), porque los scripts solo corren una vez.
- El valor `:?` en el compose hace que **falle el arranque** si falta una credencial — intencional.

---

## 4. Configuración de PostgreSQL

Archivo: `deploy/conf/postgresql.conf` (montado read-only en `/etc/postgresql/postgresql.conf`).
Se aplica al arrancar el contenedor vía argumentos `postgres -c config_file=…`.

### Bloques incluidos y su propósito

| Bloque | Parámetros clave | Beneficio |
|---|---|---|
| Conexiones | `max_connections=300`, reservados para superusuario | Capacidad POS + backend |
| Memoria | `shared_buffers`, `effective_cache_size`, `work_mem`, `maintenance_work_mem` | Cache y sorts eficientes |
| Paralelismo | `max_parallel_workers(_per_gather)=8/4`, `jit=off` | Vistas de dashboard rápidas sin penalizar tickets |
| Planner | `random_page_cost=1.1`, `effective_io_concurrency=200` | Aprovecha SSD/NVMe |
| WAL/checkpoints | `max_wal_size=2GB`, `checkpoint_completion_target=0.9`, `wal_compression=on` | Escritura fluida en ventas rápidas |
| Autovacuum | 4 workers, naptime 30 s, scale_factor 0.05 | Tablas calientes (`ventas`, detalles, kardex) sin bloat |
| Observabilidad | `pg_stat_statements`, `track_io_timing`, log >500 ms | Detectar queries lentas |
| Zona horaria | `timezone='America/Mexico_City'` | **Regla global**: todas las fechas locales |

### Fórmulas para escalar (edición manual)

```
shared_buffers       ≈ 25 % de la RAM total
effective_cache_size ≈ 60–75 % de la RAM total
work_mem             ≈ RAM / (max_connections × 4)     ← con moderación
maintenance_work_mem ≈ 5–10 % de la RAM
```

Ver perfiles listos por tamaño de servidor en la [sección 13](#13-guía-de-escalamiento-vertical).

---

## 5. Seguridad de acceso

### Roles del sistema

| Rol | Origen | Uso |
|---|---|---|
| `postgres` | imagen oficial | Administración (nunca para la app) |
| `ferreteria_app` | script 01, password vía `99_app_password.sh` | Único rol para POS/backend (conecta por PgBouncer) |
| `replicator` | `00_replica_user.sh` | Solo streaming replication |
| `admin` (usuario app) | script 04 | Usuario humano ADMINISTRADOR (login del sistema) |

### `pg_hba.conf` (resumen)

```
host ferreteria  ferreteria_app  0.0.0.0/0   scram-sha-256   # app
host replication replicator      0.0.0.0/0   scram-sha-256   # réplica
# resto denegado implícito
```

Autenticación SCRAM-SHA-256 end-to-end (PgBouncer guarda las claves en texto plano
en su `userlist.txt` generado al vuelo — proteger el host accordingly).

### Red

- **Exponer solo 6432** hacia la LAN si hay cajas remotas; nunca publicar 5432 fuera del host.
- Firewall (si aplica):

```bash
sudo firewall-cmd --add-port=6432/tcp --permanent && sudo firewall-cmd --reload
```

---

## 6. Arranque paso a paso

```bash
# 0) Ubicarse en el directorio de despliegue
cd ferreteriaDB/deploy

# 1) Crear configuración de credenciales/recursos
cp .env.example .env && nano .env

# 2) Levantar primario + pool
podman-compose up -d
#    equivalente:  podman compose up -d

# 3) Observar la inicialización (primer arranque tarda ~60–90 s)
podman logs -f ferreteria-postgres-primary
#    Esperar a ver:  "PASO 3 COMPLETO" ... "Datos demo cargados correctamente"
#    y el estado healthy:  podman ps

# 4) Probar la aplicación a través del pool
podman run --rm --network ferreteria-db_db-backend postgres:17-alpine \
  psql "host=ferreteria-pgbouncer port=6432 dbname=ferreteria \
        user=ferreteria_app password=<POSTGRES_APP_PASSWORD>" \
  -c "SELECT * FROM ven.vw_resumen_dashboard;"
```

Desde la máquina host, PgBouncer queda publicado en `localhost:6432`.

**Detener / reanudar / destruir:**

```bash
podman-compose stop          # detiene contenedores (conserva datos)
podman-compose start         # reanuda
podman-compose down          # quita contenedores y red (conserva volúmenes)
podman-compose down -v       # ⚠️ BORRA TAMBIÉN LOS DATOS (volúmenes pg_data_*)
```

> Reinicializar desde cero = `down -v` + `up -d` (los scripts vuelven a correr solo si el volumen está vacío).

---

## 7. Conexiones

| Quién | Host:puerto | Usuario | Nota |
|---|---|---|---|
| **POS / Backend Java** | `localhost:6432` (LAN: `<host>:6432`) | `ferreteria_app` | **Siempre por aquí** |
| Administración/DBA | `localhost:5432` | `postgres` | Directo al primario |
| Reportes/bi (con réplica) | `localhost:5433` | `ferreteria_app` | Solo lectura (`pg_is_in_recovery()=t`) |

**JDBC (Spring Boot) — cadena recomendada:**

```properties
spring.datasource.url=jdbc:postgresql://localhost:6432/ferreteria
spring.datasource.username=ferreteria_app
spring.datasource.password=${POSTGRES_APP_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20
```

> Con PgBouncer en modo `transaction`: no usar sesiones preparadas con nombre
> (HikariCP ya lo maneja); evitar `SET` de sesión persistente entre transacciones.
> Para auditoría por usuario: enviar `options=-c%20app.usuario_id%3D7` o ejecutar
> `SELECT set_config('app.usuario_id','<id>',true)` al inicio de cada transacción.

**psql directo (host):**

```bash
psql "host=localhost port=6432 dbname=ferreteria user=ferreteria_app password=..."
```

---

## 8. Verificación del sistema

```bash
# Estado y salud
podman ps --format "{{.Names}}\t{{.Status}}"
# → ferreteria-postgres-primary  Up X minutes (healthy)
# → ferreteria-pgbouncer         Up X minutes (healthy)

# KPIs consolidados (debe devolver una fila)
podman exec ferreteria-postgres-primary psql -U postgres -d ferreteria \
  -c "SELECT * FROM ven.vw_resumen_dashboard;"

# Dinero esperado en caja (turno abierto)
podman exec ferreteria-postgres-primary psql -U postgres -d ferreteria \
  -c "SELECT caja, fondo_inicial, entradas_efectivo, salidas_efectivo,
             dinero_esperado_en_caja FROM fin.vw_dinero_en_caja WHERE estado='ABIERTO';"

# Integridad kardex ↔ inventario (debe regresar 0 filas)
podman exec ferreteria-postgres-primary psql -U postgres -d ferreteria -c "
SELECT p.codigo FROM inv.inventario i JOIN inv.productos p USING (producto_id)
LEFT JOIN inv.movimientos_inventario m ON m.producto_id=i.producto_id AND m.almacen_id=i.almacen_id
WHERE i.almacen_id=(SELECT MIN(almacen_id) FROM inv.almacenes)
GROUP BY p.codigo, i.stock
HAVING i.stock <> COALESCE(SUM(CASE WHEN m.tipo='ENTRADA' THEN m.cantidad ELSE -m.cantidad END),0);"

# Pool: clientes conectados y uso real de conexiones
podman exec ferreteria-pgbouncer /opt/pgbouncer/pgbouncer -V
podman exec ferreteria-postgres-primary \
  psql "host=localhost port=6432 dbname=pgbouncer user=ferreteria_app password=<APP_PASS>" \
  -c "SHOW POOLS;" -c "SHOW STATS;"
```

Datos demo esperados: **10 ventas**, **3 compras**, **70 tablas**, turno abierto con
`dinero_esperado_en_caja ≈ 6830` (apertura 3000 + cobros − nómina).

---

## 9. Operación diaria

### 9.1 Logs y queries lentas

```bash
podman logs --tail 100 ferreteria-postgres-primary          # errores/checkpoints
podman logs ferreteria-postgres-primary 2>&1 | grep duration # queries >500 ms
```

Top consumidoras de tiempo (requiere `pg_stat_statements`, ya precargado):

```sql
SELECT query, calls, round(mean_exec_time::numeric,1) ms_avg
FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;
```

### 9.2 Respaldo lógico (diario recomendado)

```bash
mkdir -p ../backups
podman exec ferreteria-postgres-primary \
  pg_dump -Fc -U postgres -d ferreteria \
  > ../backups/ferreteria_$(date +%Y%m%d_%H%M).dump
```

Cron ejemplo (02:00 diario):

```cron
0 2 * * * cd /ruta/ferreteriaDB/deploy && podman exec ferreteria-postgres-primary pg_dump -Fc -U postgres -d ferreteria > ../backups/ferreteria_$(date +\%Y\%m\%d).dump
```

### 9.3 Restauración

```bash
# En BD limpia (o tras down -v && up -d primary solamente)
cat respaldo.dump | podman exec -i ferreteria-postgres-primary \
  pg_restore -U postgres -d ferreteria --clean --if-exists
```

### 9.4 Cambiar contraseñas después del primer arranque

```bash
podman exec ferreteria-postgres-primary psql -U postgres -d ferreteria \
  -c "ALTER ROLE ferreteria_app PASSWORD '<NUEVA>';"
# luego actualizar .env para que PgBouncer y futuros reinicios usen la nueva
podman-compose up -d --force-recreate pgbouncer
```

### 9.5 Corte de caja y turnos (flujo normal del sistema)

Los cortes se hacen desde la aplicación (`F.CAJA_CORTAR`): cierra el turno con
`monto_contado`; el sistema calcula `diferencia`. Nunca cerrar turnos editando tablas a mano.

---

## 10. Réplica de lectura

```bash
cd deploy
podman compose --profile replica up -d     # o podman-compose --profile replica up -d
```

- Al primer arranque ejecuta `pg_basebackup` desde el primario y entra en modo standby.
- Publicada en `localhost:5433` (solo lectura).
- Verificar replicación:

```sql
-- En primario:
SELECT client_addr, state, sync_state FROM pg_stat_replication;
-- En réplica:
SELECT pg_is_in_recovery();   -- debe ser t
```

Usarla para dashboards/reportes; el POS sigue en el primario vía 6432.

---

## 11. Autoarranque al boot con systemd

Rootless Podman no reinicia contenedores tras reiniciar el equipo salvo que se integre con systemd (**Quadlet**, podman ≥4.4).

`~/.config/containers/systemd/ferreteria-db.container`:

```ini
[Unit]
Description=Ferreteria PostgreSQL Primario
After=network-online.target

[Container]
Image=docker.io/library/postgres:17-alpine
ContainerName=ferreteria-postgres-primary
EnvironmentFile=%h/Documentos/Proyectos/JAVA/ferreteriaDB/deploy/.env
Network=ferreteria-db_db-backend
PublishPort=5432:5432
Volume=pg_data_primary:/var/lib/postgresql/data
Mount=type=bind,src=%h/Documentos/Proyectos/JAVA/ferreteriaDB/deploy/conf/postgresql.conf,target=/etc/postgresql/postgresql.conf,options=rro,Z
Mount=type=bind,src=%h/Documentos/Proyectos/JAVA/ferreteriaDB/deploy/conf/pg_hba.conf,target=/etc/postgresql/pg_hba.conf,options=rro,Z
Exec=postgres -c config_file=/etc/postgresql/postgresql.conf
HealthCmd=pg_isready -U postgres -d ferreteria -q
HealthInterval=15s

[Service]
Restart=always

[Install]
WantedBy=default.target
```

(Análogo un `…-pgbouncer.container`). Activar:

```bash
systemctl --user daemon-reload
systemctl --user start ferreteria-db.service
loginctl enable-linger $USER      # arranque sin haber iniciado sesión gráfica
```

Alternativa rápida (legacy): `podman generate systemd --new --name ferreteria-postgres-primary`.

---

## 12. Solución de problemas conocidos

| Síntoma | Causa raíz | Solución aplicada / acción |
|---|---|---|
| Log dice `ignoring /docker-entrypoint-initdb.d/…` y la BD sale vacía | El entrypoint oficial ignora **subdirectorios** en `initdb.d` | Los montajes son archivos planos prefijados (`00_roles.sh`, `10_01….sql`, …) — no agrupar en carpetas |
| `ERROR: role "ferreteria_app" does not exist` durante init | Un `.sh` corría antes que los `.sql` que crean el rol | La contraseña de app se aplica en `99_app_password.sh` (último en orden) |
| `pgbouncer: not found` en logs del pool | Binario oficial vive en `/opt/pgbouncer/pgbouncer` | El comando usa ruta absoluta; `entrypoint: []` neutraliza el validador propio de la imagen |
| `manifest unknown` al bajar imagen de PgBouncer | Tag inexistente en Docker Hub | Usar `pgbouncer/pgbouncer:latest` o fijar digest válido en `.env` |
| `Permission denied` montando configs (Fedora/RHEL con SELinux) | Etiqueta SELinux de bind mounts | Agregar sufijo `:Z` (o `:z`) a cada mount de `conf/` en docker-compose.yml |
| Puerto ocupado `5432/6432 already in use` | Otro servicio local | Cambiar publicación en compose (`5433:5432`, etc.) o liberar el puerto |
| `El turno X no está abierto` al vender | Turno cerrado o no creado | Abrir turno desde la app (`F.CAJA_ABRIR`); los movimientos de caja exigen turno ABIERTO |
| `Stock insuficiente producto …` | Venta sin existencias y `permitir_stock_negativo=false` | Comprar/ajustar inventario, o cambiar la clave en `cfg.configuracion` |
| Volumen creció pero quiero recargar demo | Scripts solo corren con volumen vacío | `podman-compose down -v` ⚠️ borra datos → `up -d` |

Diagnóstico general:

```bash
podman ps -a | grep ferreteria
podman inspect ferreteria-postgres-primary --format '{{.State.Health.Log}}'
podman network ls | grep ferreteria
podman volume ls  | grep pg_data
```

---

## 13. Guía de escalamiento vertical

Editar `.env` (`PG_MEM_LIMIT`, `PG_CPUS`, `PG_SHM_SIZE`) **y** `conf/postgresql.conf`
(según tabla de fórmulas de la sección 4). Reiniciar: `podman-compose restart postgres-primary`.

| Recurso | Perfil CAJA (4 GB) | Perfil TIENDA (16 GB) | Perfil CADENA (64 GB+) |
|---|---|---|---|
| `PG_MEM_LIMIT` | 2g | 8g | 32g |
| `PG_CPUS` | 2.0 | 6.0 | 16.0 |
| `shared_buffers` | 512MB | 4GB | 16GB |
| `effective_cache_size` | 1536MB | 12GB | 48GB |
| `work_mem` | 16MB | 32MB | 64MB |
| `maintenance_work_mem` | 256MB | 1GB | 4GB |
| `max_parallel_workers_per_gather` | 4 | 6 | 8 |
| `max_connections` | 300 | 300 | 500 |
| `PG_SHM_SIZE` | 256mb | 512mb | 1g |
| Almacenamiento | SSD SATA | NVMe | NVMe RAID |

Horizontal (varias sucursales/picos): activar réplica (§10) para reportes y migrar a
Kubernetes (`deploy/k8s/` o `deploy/terraform/`) donde PgBouncer escala 2→6 réplicas automáticamente.

---

## 14. Checklist de producción

- [ ] Contraseñas fuertes únicas en `.env` (admin/app/replicación) — sin valores de ejemplo
- [ ] Imágenes fijadas por **digest** (`@sha256:…`) en `.env`
- [ ] `05_dummy.sql` NO debe existir en el directorio de scripts (o usar `deploy/terraform` con `load_demo_data=false`)
- [ ] Firewall: exponer **solo 6432**; 5432/5433 locales
- [ ] Respaldo programado (sección 9.2) + **prueba de restauración** mensual
- [ ] Quadlet/systemd habilitado + `loginctl enable-linger`
- [ ] Monitoreo: alertas sobre contenedor unhealthy, conexiones (`SHOW POOLS`), autovacuum y réplicas caídas
- [ ] Rotación de logs de podman (`events_logger=journald`) y espacio en disco del volumen
- [ ] Cambiar password del usuario `admin` del sistema en el primer login de la aplicación
- [ ] Revisar semanalmente `vw_stock_bajo` y `com.vw_facturas_vencidas` (procesos de compra/pago)

---

*Documento operativo generado para el proyecto Ferretería. Referencia técnica completa del
modelo de datos: `../base_de_datos_ferreteria.md`.*
