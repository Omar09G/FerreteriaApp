# ferreteria-db — PostgreSQL del Sistema Integral de Ferretería

Base de datos del sistema (motor objetivo PostgreSQL 14+, zona `America/Mexico_City`).
Despliegue (dev, clúster y código): [`deploy/README.md`](deploy/README.md).

## Estructura

| Ruta | Contenido |
|---|---|
| `scripts/` | **Fuente de verdad** del esquema: 01 base/esquemas, 02 tablas, 03 parámetros (semilla), 04 admin, 05 dummy (demo) y `vistas_core.sql` |
| `migrations/` | Deltas SQL (`delta_*.sql`) para evolución incremental del esquema |
| `deploy/` | Despliegue: Podman Compose, Kubernetes (`k8s/`), Terraform (`terraform/`) y guía Podman |

## Convenciones

- `TIMESTAMPTZ` en todas las columnas de fecha/hora; la BD y el rol de aplicación se
  configuran con `timezone = 'America/Mexico_City'`.
- Integridad centralizada en funciones/triggers y validaciones de negocio en CHECKs.
- Esquemas por módulo: `cat`, `cfg`, `rh`, `seg`, `inv`, `com`, `ven`, `fin`, `fis`.
- Índices estratégicos, vistas de negocio (incluidas las del dashboard) y datos semilla.

## Cómo se usa desde el backend

Los scripts de `scripts/` son la fuente de verdad: `ferreteriaBackend` los consolida en
migraciones Flyway V1/V2 con `./gradlew generateMigrations` y las aplica al arrancar
(perfil productivo: Flyway únicamente, sin datos demo; perfil `demo`: además carga
`05_dummy.sql`).

Para cambios al esquema: editar `scripts/` y regenerar, o agregar un delta en `migrations/`.

## Despliegue rápido (dev)

```bash
cd deploy
cp .env.example .env          # editar credenciales
podman compose up -d          # PostgreSQL + PgBouncer listos
```

> Aplicación usa el puerto del PgBouncer (ver `deploy/.env`). Más opciones (k8s/terraform)
> en [`deploy/README.md`](deploy/README.md).