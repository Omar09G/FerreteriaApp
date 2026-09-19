# AGENTS.md — Sistema Integral de Ferretería

Monorepo (ES docs): `ferreteriaDB/` (PostgreSQL, schema source of truth) ·
`ferreteriaBackend/` (Spring Boot 3 / Java 21, `/api/v1` on `:8080`) ·
`ferreteriaFront/` (React 19 + Vite 8 + TS, dev on `:5173`).
Root `README.md` + per-project READMEs are accurate; trust them over memory.
`CLAUDE.md` holds graphify rules — use `graphify query/path/explain` first for
codebase questions, then `graphify update .` after code changes.

## Boot order (whole stack)

1. `cd ferreteriaDB/deploy && cp .env.example .env && podman compose up -d`
2. `cd ferreteriaBackend && ./gradlew bootRun` (needs `JWT_SECRET` in env or
   `.env`, ≥32 bytes — fail-fast, no default; `openssl rand -base64 48`)
3. `cd ferreteriaFront && npm install && npm run dev`

App connects via PgBouncer `localhost:6432` as `ferreteria_app`, never direct
`5432` (admin only). Backend `bootRun` auto-loads `./.env` (real env wins);
frontend never reads DB `.env` — Vite bakes `VITE_*` at build time.

## Backend (`ferreteriaBackend/`)

- `./gradlew build` = compile + tests + JaCoCo gates (≥80% global,
  ≥85% `common/i18n`, `common/error`, `common/web`); `check` enforces them.
- Single test: `./gradlew test --tests "com.example.MyTest"`.
  `*IT` (e.g. `AuthFlowIT`) need Docker/Podman socket and self-skip without it
  (`@Testcontainers(disabledWithoutDocker = true)`).
- Schema: edit `ferreteriaDB/scripts/`, then `./gradlew generateMigrations`
  (regenerates ONLY `V1__base.sql`/`V2__parametria.sql` + `db/demo/05_dummy.sql`).
  Never hand-edit generated files (header: NO EDITAR A MANO). New schema changes
  go as hand-written incremental `V<N>__*.sql` (now at V16) in the same dir AND
  mirrored in `ferreteriaDB/scripts/` (kept in sync, e.g. V16 ↔ `02_tablas.sql`).
- `ferreteriaBackend/bin/` holds tracked stale build copies — never edit or read
  as source; source is always `src/main/...`.
- Catalogs: simple ones are metadata-driven — register in `cat/catalogo/Catalogos.java`
  (served by generic `CatalogoController`), never hand-write a controller. Custom
  catalog controllers extend `AbstractCatalogoController` (carries `@PreAuthorize`:
  read = any authenticated role, create/update = GERENTE/ADMINISTRADOR,
  deactivate = ADMINISTRADOR) and must be listed in the `*SecurityTest` role×verb
  matrix or the build-adjacent tests fail. Same role pattern on domain controllers
  (VENDEDOR/ALMACENISTA per domain).
- Money totals (venta/compra/devolución) are recomputed from line items by DB
  triggers/functions (e.g. `fn_recalc_totales_venta`, V14/V16) — never trust
  client-sent totals.
- `@WebMvcTest` slices must import shared `common/web/WebMvcTestProps`
  (provides the `RateLimitProperties` bean slices don't scan).
- Profiles: default = Flyway only, no demo data; `demo` profile also loads
  `db/demo/05_dummy.sql`. Never enable `demo` in prod (`DemoGuard` fail-fast).
- Error messages live ONLY in `resources/i18n/messages_{es,en}.properties`,
  keyed by `ErrorCode` — never hardcode user text (ArchUnit test fails the
  build). DB `PERR-xxx` codes map to `ErrorCode` → HTTP (RFC 7807).
- Every request must carry `X-Request-Id` (`GENERATE` default,
  `STRICT` via `REQUEST_ID_MODE`).
- PgBouncer is `pool_mode=transaction`: JDBC URL must keep
  `prepareThreshold=0`; Hikari max 10 (budget vs PgBouncer pool / PG max conns).
- Default CORS has no wildcard with credentials; dev works via Vite `/api`
  proxy, not backend CORS. Auth travels in HttpOnly cookies (`rt`/`at`),
  frontend never reads tokens from JS.

## Frontend (`ferreteriaFront/`)

- `npm run dev` (proxy `/api → http://localhost:8080`, override
  `VITE_API_PROXY`; `VITE_DEV_SIN_PROXY=true` bypasses proxy in dev) ·
  `npm run build` (`tsc -b && vite build`) · `npm run lint` · `npm test`
  = `vitest run`; single file: `npx vitest run src/path/to.test.tsx`.
- API envelope: success `{ success, data?, meta? }`, error
  `{ success:false, errorCode, codigo, errorMessage }`. Axios interceptor
  refreshes with mutex + one retry; on failure → local logout → `/login?expired=1`.
- Reuse `components/ui/ExportarExcel.tsx` (`columnas`+`items`+`archivo`) for any
  new Excel export — ~35 pages already use it. Bulk import pattern is
  `features/catalogo/CargaMasivaDialog.tsx`. Simple catalog admin UI is the
  reusable `/catalogos/:tipo` page (`CatalogoCrudPage`), not per-catalog pages.
- Conventions: `LocalDate` as `yyyy-MM-dd`, money via
  `Intl.NumberFormat("es-MX", MXN)`, path alias `@/ → src/`.
  Keep `VITE_OTEL_ENABLED=false` for local dev.

## DB (`ferreteriaDB/`)

- `scripts/` is canonical (01 base → 02 tables + `vistas_core.sql` inlined via
  `\ir` → 03 params → 04 admin → 05 dummy/demo only). `migrations/delta_*.sql`
  for incremental changes; k8s prod uses explicit 01–04 list (never 05).
- `TIMESTAMPTZ` everywhere, `timezone = 'America/Mexico_City'`; module schemas
  `cat cfg rh seg inv com ven fin fis`. Business integrity in triggers/CHECKs.
- Compose mounts are flat files with order prefixes — the official image
  ignores subdirectories in `initdb.d`.
