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

# 2) Backend (aplica migraciones al arrancar)
cd ../../ferreteriaBackend
export PG_HOST=localhost PG_PORT=6432 PG_USER=ferreteria_app PG_PASSWORD=<ver deploy/.env>
./gradlew bootRun

# 3) Frontend
cd ../ferreteriaFront && npm install && npm run dev
```

Detalles y comandos de pruebas/build en el README de cada proyecto.

## Scripts de soporte

- `collector/` — colección de requests HTTP de apoyo (collections para probar la API).