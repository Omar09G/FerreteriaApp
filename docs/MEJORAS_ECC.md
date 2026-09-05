# MEJORAS ECC — Auditoría Integral del Monorepo Ferretería

> **Generado:** 2026-09-05 19:30 UTC  
> **Fuente de verdad:** `audits/findings.yaml`  
> **Total de hallazgos:** 316  
> **Por severidad:** `CRITICAL`=18 · `HIGH`=96 · `MEDIUM`=148 · `LOW`=54  
> **Por proyecto:** `backend`=121 · `frontend`=59 · `database`=136  
> **Modo:** READ-ONLY · No se modificó código. Este archivo es un render derivado.

Para añadir/cambiar hallazgos, editar `audits/findings.yaml` y re-ejecutar `python3 scripts/render.py` (ver `scripts/README.md`).

---

## §0.1 Convención de severidad

| Tag | Criterio (umbral cuantitativo) |
|---|---|
| **CRITICAL** | Bypass de autorización · credenciales/secrets en el repo · corrupción/pérdida de datos sin backup · impacto monetario directo · crash sistémico |
| **HIGH** | Degradación >50 % performance · info disclosure sensible · N+1 en hot path · authz rota en >1 endpoint · compliance failure |
| **MEDIUM** | Deuda técnica con workaround · <50 % perf · tests ausentes en lógica crítica · DX pobre |
| **LOW** | Cosmético · inconsistencia estilística · doc ausente |

Para re-clasificar, editar el campo `severity` en `findings.yaml` y re-renderizar.

---

## §0 TL;DR — Conteos canónicos

| Proyecto | CRITICAL | HIGH | MEDIUM | LOW | Total |
|---|---:|---:|---:|---:|---:|
| `backend` (ferreteriaBackend) | 14 | 39 | 49 | 19 | **121** |
| `frontend` (ferreteriaFront) | 1 | 11 | 32 | 15 | **59** |
| `database` (ferreteriaDB) | 3 | 46 | 67 | 20 | **136** |
| **TOTAL** | **18** | **96** | **148** | **54** | **316** |

Estos conteos son la única verdad de severidad. Cualquier otra tabla en este documento es un subset.

---

## §1 Top 10 CRITICAL (orden de remediación)

- **CRITICAL** `DB-ESC-002` seg — `scripts/02_tablas.sql:208-220` — seg.auditoria BIGINT, sin partition; **~315 M filas/año ≈ 1 TB/año**
  - **Acción**: PARTITION + TOAST compression + archivado _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **CRITICAL** `BACK-SEC-001` cat/api/ConfiguracionController — `02_tablas.sql:1229-1233` — cat/api/ConfiguracionController.java + ConfiguracionService.java — PUT /api/v1/configuraciones/{clave} deja a cualquier usuario autenticado flipear cfg.configuracion.permitir_stock_negativo (verificado por )
  - **Acción**: restringir a ADMINISTRADOR _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **CRITICAL** `BACK-SEC-002` JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc= — `application.yml:43` — JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc=. Si falta env var, arranca con secreto commiteado
  - **Acción**: quitar default; @PostConstruct fail-fast _(blast_radius: `security`; confidence: `HIGH`)_

- **CRITICAL** `DB-SEC-001` CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION' — `scripts/01_base_esquemas.sql:29` — CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION'. Si se ejecuta tal cual, login con password trivial
  - **Acción**: ALTER ROLE … WITH PASSWORD NULL _(blast_radius: `security`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-001` toResponse ejecuta clienteRepo — `ven/service/VentaService.java:165-204` — toResponse ejecuta clienteRepo.findById + almacenRepo.findById + formaPagoRepo.findById + detalleRepo.findByVentaId + por cada detalle productoRepo.findById + cuentaRepo.findByVentaId + pagoRepo.findByCuentaCobrarIdOrderByFechaDesc — N+1 g…
  - **Acción**: findAllById(...) batch + Map<Long,T> en mapper _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-002` mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle — `com/service/CompraService.java:198-217` — mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-003` mismo patrón — `ven/service/CotizacionService.java:120-138` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-004` toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 — `ven/service/CreditoService.java:43-63` — toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 severo en /api/v1/creditos/cobranza.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-005` mismo patrón — `ven/service/DevolucionService.java:66-83` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-006` mismo patrón — `ven/service/RentaService.java:109-122` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

### Otros CRITICAL (8)

- **CRITICAL** `BACK-REND-007` listCajas/listTurnos/toCorteResponse iteran almacenRepo — `fin/service/CajaService.java:96-101, 244-262` — listCajas/listTurnos/toCorteResponse iteran almacenRepo.findById y cajaRepo.findById por cada elemento.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-008` listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway — `seg/service/SegAdminService.java:69-75, 121-126, 165-170` — listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway.findX(limit, offset) + lookups en map() (N+1). Paginación manual con offset = page * size que degrada con páginas altas.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-009` paginación manual con gateway — `seg/service/AuditoriaService.java:36-48` — paginación manual con gateway.buscar() + gateway.contar() separado; OFFSET degrada con filtros ILIKE.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-010` generarQuincena y pagarLote hacen jdbc — `rh/service/NominaService.java:94-100, 137-141, 178-184` — generarQuincena y pagarLote hacen jdbc.queryForObject por cada fila (COUNT(*) duplicado, nombre_completo empleado). N+1.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-011` list invoca detalleRepo — `inv/service/TrasladoService.java:43-50, 132-145` — list invoca detalleRepo.findByTrasladoId dentro de page.map (N+1); create hace 2 jdbc.queryForObject para candidatos por detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-012` cuando llega almacenId, hace inventarioRepo — `cat/service/ProductoService.java:38-69` — cuando llega almacenId, hace inventarioRepo.findByAlmacenIdAndProductoId por cada producto de la página.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `DB-ESC-001` **ninguna tabla ledger particionada** — `scripts/02_tablas.sql:314-331, 634-697, 875-912, 943-983` — **ninguna tabla ledger particionada**. Tienda mediana 5 años = 900 k ventas + 4.5-9 M venta_detalles
  - **Acción**: PARTITION BY RANGE mensual con pg_partman _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `FRONT-MAN-001` [CR #7] — **sin test script, sin vitest/jest, sin @testing-library** — `package.json:6-11` — [CR #7] — **sin test script, sin vitest/jest, sin @testing-library**. Búsqueda confirma **0 archivos *.test.*/*.spec.*** en src/. Riesgo crítico en POS/finanzas.
  - **Acción**: (definir acción concreta) _(blast_radius: `regression`; confidence: `HIGH`)_

---

## §2 Hallazgos transversales (cross-cutting)

Agrupados por `blast_radius`, severidad >= HIGH.

### Pérdida de datos

- **CRITICAL** `DB-ESC-002` seg — `scripts/02_tablas.sql:208-220` — seg.auditoria BIGINT, sin partition; **~315 M filas/año ≈ 1 TB/año**
  - **Acción**: PARTITION + TOAST compression + archivado _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **HIGH** `DB-REND-005` seg — `scripts/02_tablas.sql:206-220` — seg.auditoria sin partitioning/retention. ~315 M filas/año.
  - **Acción**: (definir acción concreta) _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **HIGH** `DB-REND-006` inv — `scripts/02_tablas.sql:314-331` — inv.movimientos_inventario (BIGINT IDENTITY, append-only, sin partition). 100-500 k filas/mes
  - **Acción**: RANGE partition por creado_en mensual _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **HIGH** `DB-REND-007` ven — `scripts/02_tablas.sql:634-697, 875-912, 943-983` — ven.ventas, fin.movimientos_caja, fin.cortes_caja sin partitioning.
  - **Acción**: (definir acción concreta) _(blast_radius: `data_loss`; confidence: `HIGH`)_

### DX / Operación

- **HIGH** `BACK-DIS-001` solo 2 reglas ArchUnit activas — `architecture/MensajesSoloDesdeErrorCodeTest.java:36-47` — solo 2 reglas ArchUnit activas. Faltan: @Service solo en *Service, @RestController solo en *Controller, gateways solo consumidos por service/, repositories no dependen de controllers, services no inyectan repos de otro módulo, DTOs no usan…
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-DIS-002` @PersistenceContext EntityManager dentro de service (detalle de infra en service — `ven/service/VentaService.java:53-54` — @PersistenceContext EntityManager dentro de service (detalle de infra en service). Rompe hexagonal/layered
  - **Acción**: extraer VentaWriteRepository _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-001` catch (Exception e) { omitidas++; } traga TODA excepción sin loguear — `rh/service/NominaService.java:118-124` — catch (Exception e) { omitidas++; } traga TODA excepción sin loguear. Imposible diagnosticar nóminas faltantes
  - **Acción**: LOG.warn _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-002` checkout ejecuta ventaRepo — `ven/service/VentaService.java:128-138` — checkout ejecuta ventaRepo.flush() + em.refresh(savedVenta) + em.refresh(detalle) por cada detalle. Errores no-RuntimeException pueden dejar sesión JPA rota
  - **Acción**: usar RETURNING en lugar de refresh _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-003` refresh() con dos refresh concurrentes del mismo cliente (mismo hash) provoca TO — `seg/service/AuthService.java:141-219` — refresh() con dos refresh concurrentes del mismo cliente (mismo hash) provoca TOKEN_EXPIRADO
  - **Acción**: SELECT FOR UPDATE sobre seg.refresh_tokens.token_hash _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-004` create recorre req — `inv/service/TrasladoService.java:90-110` — create recorre req.detalles() con productoRepo.findById (1 query por producto) + 2 MovimientoInventario.save por detalle. Para 100 SKUs = 200 inserts sin flush periódico
  - **Acción**: em.flush() cada N inserts _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-UI-001` POST /auth/login retorna 200 (OK para sesión); POST /auth/register también 200 — — `seg/api/AuthController.java:46-53, 79-90` — POST /auth/login retorna 200 (OK para sesión); POST /auth/register también 200 — registro sí crea recurso, debería ser 201. Confirmar convención.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-UI-002` solo declara bearerJWT — `common/config/OpenApiConfig.java:18-27` — solo declara bearerJWT. La auth real es por cookies HttpOnly (at, rt). Swagger "Authorize" con Bearer <token> falla
  - **Acción**: esquema cookieAuth o documentar el flujo _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-UI-003` solo documenta success/data/meta para páginas — `common/web/EnvelopeAdvice.java:50-58` — solo documenta success/data/meta para páginas. Errores no documentados en OpenAPI.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-001` convención de PK inconsistente (BIGINT vs INTEGER) — `scripts/02_tablas.sql:485, 591, 635, 876, 915`
  - **Acción**: BIGINT para transaccional, INTEGER solo catálogos con cardinalidad acotada _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-002` cat — `scripts/02_tablas.sql:34, 78, 80` — cat.categorias.nombre VARCHAR(100) NOT NULL sin UNIQUE global
  - **Acción**: UNIQUE (padre_id, nombre) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-003` ven — `scripts/02_tablas.sql:621` — ven.cotizaciones.venta_generada_id BIGINT sin FK declarada en CREATE TABLE (FK se añade después)
  - **Acción**: FK inline o DEFERRABLE INITIALLY DEFERRED _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-004` columnas GENERATED ALWAYS AS … STORED no documentadas como "no incluir en INSERT — `scripts/02_tablas.sql:464, 670, 813, 959-961` — columnas GENERATED ALWAYS AS … STORED no documentadas como "no incluir en INSERT"
  - **Acción**: documentar o BEFORE INSERT triggers _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-005` funciones de totales mezclan cálculo con transición de estado — `scripts/02_tablas.sql:1354-1356, 1508, 1754-1761, 1430-1434, 1595-1599`
  - **Acción**: separar en funciones puras testeables (fn_calcular_estado_cuenta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-006` fecha_local DATE GENERATED … depende del GUC timezone; PgBouncer transaction mod — `scripts/02_tablas.sql:642, 705, 793, 612` — fecha_local DATE GENERATED … depende del GUC timezone; PgBouncer transaction mode puede no propagarlo
  - **Acción**: ALTER ROLE … SET timezone=… (ya existe L36) y/o cast explícito _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-007` funciones API en plpgsql cuando son esencialmente INSERT/UPDATE — `scripts/02_tablas.sql:1066-1081, 1840-1866, 1873-1895`
  - **Acción**: LANGUAGE sql para inline en el planner _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-008` soft-delete model mixto — `scripts/02_tablas.sql:206-220, 314-331, 875-912, 943-983`
  - **Acción**: unificar con eliminado_en TIMESTAMPTZ + vista vw_<tabla>_activas _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-001` trg_mov_stock (AFTER INSERT) puede dejar stock desincronizado con rollback parci — `scripts/02_tablas.sql:1218-1246, 1248-1254` — trg_mov_stock (AFTER INSERT) puede dejar stock desincronizado con rollback parcial
  - **Acción**: BEFORE INSERT + UPDATE atómico _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-002` ven — `scripts/02_tablas.sql:1322-1326` — ven.fn_valida_credito sin FOR UPDATE sobre ven.cuentas_cobrar → race condition "credit check + write"
  - **Acción**: LOCK TABLE o SELECT … FOR UPDATE _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-003` fn_recalc_totales_venta/compra AFTER-per-row disparan N veces por venta → riesgo — `scripts/02_tablas.sql:1330-1420, 1499-1585` — fn_recalc_totales_venta/compra AFTER-per-row disparan N veces por venta → riesgo de deadlock entre ven.ventas, ven.cuentas_cobrar, ven.pagos_cliente
  - **Acción**: trigger STATEMENT-level + pg_trigger_depth()=0 _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-004` FOR UPDATE sin ORDER BY consistente → deadlocks intermitentes — `scripts/02_tablas.sql:1364, 1403, 1516, 1558, 1696, 1845`
  - **Acción**: documentar convención de lock acquisition; considerar SKIP LOCKED _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-005` fin — `scripts/02_tablas.sql:1718-1739` — fin.fn_cerrar_turno toma FOR UPDATE sobre fin.turnos_caja pero NO sobre fin.movimientos_caja → movimientos fantasma
  - **Acción**: LOCK TABLE fin.movimientos_caja IN SHARE MODE _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-006` cfg — `scripts/02_tablas.sql:1066-1081` — cfg.fn_siguiente_folio sin FOR UPDATE → dos INSERTs concurrentes leen mismo consecivo
  - **Acción**: UPDATE … SET consecutivo = consecutivo + 1 … RETURNING _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-007` deploy/conf/pgbouncer — PgBouncer pool_mode=transaction + DISCARD ALL; current_s — `deploy/docker-compose.yml:431-434,` — deploy/conf/pgbouncer — PgBouncer pool_mode=transaction + DISCARD ALL; current_setting('app.usuario_id', true) depende de SET LOCAL
  - **Acción**: documentar que backend DEBE usar SET LOCAL app.usuario_id = $1 _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-008` triggers AFTER INSERT/UPDATE/DELETE → seg — `scripts/02_tablas.sql:1135, 1138, 1141, 1144, 1147, 1150, 1153` — triggers AFTER INSERT/UPDATE/DELETE → seg.fn_auditar → INSERT en seg.auditoria; si tablespace lleno, cascada de errores
  - **Acción**: monitor + alerta + partition + archivado _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-009` fn_cerrar_turno filtra pérdidas por m — `scripts/02_tablas.sql:1742-1750` — fn_cerrar_turno filtra pérdidas por m.creado_en BETWEEN v_apertura_en AND now(); turnos que abren 00:01 pierden movimientos del día anterior por desfase de zona
  - **Acción**: usar fecha_local o ±1h tolerancia _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-010` trg_kardex_no_upd BEFORE UPDATE OR DELETE no impide TRUNCATE — `scripts/02_tablas.sql:1252-1254`
  - **Acción**: REVOKE TRUNCATE + trigger BEFORE TRUNCATE _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-001` **1921 líneas** en un solo archivo con secciones A-Ñ — `scripts/02_tablas.sql:1-1921`
  - **Acción**: dividir (02_tablas_cat.sql, 02_tablas_ven.sql, 02_funciones.sql, 02_triggers.sql, 02_grants.sql) + \ir _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-002` cada trigger con DROP TRIGGER IF EXISTS …; CREATE TRIGGER …; (~30 bloques) — `scripts/02_tablas.sql:1115-1156, 1244-1299, 1422-1466, 1495-1611, 1643-1675`
  - **Acción**: helper procedure _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-003` 25+ funciones sin namespace — `scripts/02_tablas.sql:1066, 1083, 1116, 1157, 1172, 1199, 1218, 1248, 1257, 1284, 1302, 1330, 1427, 1449, 1469, 1499, 1592, 1614, 1633, 1647, 1663, 1678, 1787, 1837, 1869` — 25+ funciones sin namespace. Documentar convención.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-004` funciones completas (re-implementación) en lugar de patch — `migrations/delta_errcodes_negocio.sql:10-288`
  - **Acción**: Flyway R__ repeatable _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-005` DATA_SOURCE_NAME con POSTGRES_ADMIN_PASSWORD interpolado — `deploy/docker-compose.yml:191-194`
  - **Acción**: secret refs _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-006` Job ejecuta con psql … -v ON_ERROR_STOP=1; orden incorrecto falla sin claridad — `deploy/k8s/40-migration-job.yaml:30-49`
  - **Acción**: logging + verificación _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-007` DO $$ para FKs con IF NOT EXISTS sobre pg_constraint — `scripts/02_tablas.sql:1049-1059`
  - **Acción**: ALTER TABLE … ADD CONSTRAINT IF NOT EXISTS (PG 16+) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-ACC-001` serialización children a string — `src/components/ui/ConfirmDialog.tsx:65-71` — serialización children a string. JSX complejo (<p>¿Seguro? <strong>{name}</strong></p>) se descarta por completo. Contrato frágil.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-EST-001` document — `src/components/ui/Dialog.tsx:67-82` — document.body.style.overflow = "hidden" se asigna en cada useEffect; cleanup restaura valor capturado al momento. Varios diálogos solapados → restauración incorrecta
  - **Acción**: contador de referencias activas _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-EST-002` // eslint-disable-next-line react-hooks/exhaustive-deps con deps [autenticado] — `src/hooks/useInactivityTimeout.ts:82` — // eslint-disable-next-line react-hooks/exhaustive-deps con deps [autenticado]. toast y t quedan con referencias viejas tras cambio de idioma
  - **Acción**: useRef o recrear effect _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-MAN-002` 1164 líneas, imposible de probar unitariamente sin mount completo — `src/features/pos/PosPage.tsx:1-1164` — 1164 líneas, imposible de probar unitariamente sin mount completo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-MAN-003` 4 responsabilidades mezcladas — `src/lib/api/client.ts:1-267` — 4 responsabilidades mezcladas. Refactor en client-base.ts, csrf.ts, refresh.ts, errors.ts.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-MAN-004` ↔ ↔ — **mismatch de claves i18n**: rango — `src/i18n/es/rango.ts:1-8` · `src/lib/rango.ts:55-61` · `src/components/ui/DateRangePicker.tsx:57` — ↔ ↔ — **mismatch de claves i18n**: rango.ultimos-7 no existe en i18n; botones muestran la clave cruda.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

### Fraude financiero

- **CRITICAL** `BACK-SEC-001` cat/api/ConfiguracionController — `02_tablas.sql:1229-1233` — cat/api/ConfiguracionController.java + ConfiguracionService.java — PUT /api/v1/configuraciones/{clave} deja a cualquier usuario autenticado flipear cfg.configuracion.permitir_stock_negativo (verificado por )
  - **Acción**: restringir a ADMINISTRADOR _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-003` GRANT SELECT, INSERT, UPDATE, DELETE total a ferreteria_app sobre TODAS las tabl — `scripts/02_tablas.sql:1905-1919` — GRANT SELECT, INSERT, UPDATE, DELETE total a ferreteria_app sobre TODAS las tablas (incl. seg.usuarios, cfg.configuracion, fis.facturas)
  - **Acción**: segregar roles (ferreteria_ro, ferreteria_pos, ferreteria_admin) con GRANTs específicos _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

### Rendimiento

- **CRITICAL** `BACK-REND-001` toResponse ejecuta clienteRepo — `ven/service/VentaService.java:165-204` — toResponse ejecuta clienteRepo.findById + almacenRepo.findById + formaPagoRepo.findById + detalleRepo.findByVentaId + por cada detalle productoRepo.findById + cuentaRepo.findByVentaId + pagoRepo.findByCuentaCobrarIdOrderByFechaDesc — N+1 g…
  - **Acción**: findAllById(...) batch + Map<Long,T> en mapper _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-002` mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle — `com/service/CompraService.java:198-217` — mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-003` mismo patrón — `ven/service/CotizacionService.java:120-138` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-004` toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 — `ven/service/CreditoService.java:43-63` — toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 severo en /api/v1/creditos/cobranza.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-005` mismo patrón — `ven/service/DevolucionService.java:66-83` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-006` mismo patrón — `ven/service/RentaService.java:109-122` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-007` listCajas/listTurnos/toCorteResponse iteran almacenRepo — `fin/service/CajaService.java:96-101, 244-262` — listCajas/listTurnos/toCorteResponse iteran almacenRepo.findById y cajaRepo.findById por cada elemento.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-008` listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway — `seg/service/SegAdminService.java:69-75, 121-126, 165-170` — listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway.findX(limit, offset) + lookups en map() (N+1). Paginación manual con offset = page * size que degrada con páginas altas.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-009` paginación manual con gateway — `seg/service/AuditoriaService.java:36-48` — paginación manual con gateway.buscar() + gateway.contar() separado; OFFSET degrada con filtros ILIKE.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-010` generarQuincena y pagarLote hacen jdbc — `rh/service/NominaService.java:94-100, 137-141, 178-184` — generarQuincena y pagarLote hacen jdbc.queryForObject por cada fila (COUNT(*) duplicado, nombre_completo empleado). N+1.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-011` list invoca detalleRepo — `inv/service/TrasladoService.java:43-50, 132-145` — list invoca detalleRepo.findByTrasladoId dentro de page.map (N+1); create hace 2 jdbc.queryForObject para candidatos por detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-012` cuando llega almacenId, hace inventarioRepo — `cat/service/ProductoService.java:38-69` — cuando llega almacenId, hace inventarioRepo.findByAlmacenIdAndProductoId por cada producto de la página.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `DB-ESC-001` **ninguna tabla ledger particionada** — `scripts/02_tablas.sql:314-331, 634-697, 875-912, 943-983` — **ninguna tabla ledger particionada**. Tienda mediana 5 años = 900 k ventas + 4.5-9 M venta_detalles
  - **Acción**: PARTITION BY RANGE mensual con pg_partman _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-001` Caffeine local por instancia — `common/web/RateLimitInterceptor.java:43-71` — Caffeine local por instancia. Con N réplicas, límite efectivo N×capacidad
  - **Acción**: bucket4j-redis o Hazelcast _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-002` Hikari maximum-pool-size=10 sin auto-tuning ni connection timeout agresivo para — `application.yml:14-18` — Hikari maximum-pool-size=10 sin auto-tuning ni connection timeout agresivo para fallos. Documentar dimensionamiento para N cajas.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-003` + — catálogo promociones/descuentos se consulta cada venta — `ven/service/CotizacionService.java:51-58` · `PromocionService.java:65-87`
  - **Acción**: @Cacheable("promocionesActivas") TTL 60s _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-004` todos los catálogos sin cache — `cat/service/AbstractCatalogoService.java:30-35` — todos los catálogos sin cache. Endpoints de frontend (puestos, marcas, UMs, categorías, formas de pago, formas SAT, usos CFDI) golpean BD cada load
  - **Acción**: cache 5 min _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-005` cat/repo/ProductoRepository — `01_base_esquemas.sql:75-79` · `V1__base.sql:38` — cat/repo/ProductoRepository.java — búsqueda por texto en nombre/codigo sin índice trigram. La migración declara pg_trgm (línea comentada en ) — verificar índice GIN/trigram existe para inv.productos.codigo y nombre.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-013` idx_ventas_fecha_local faltante — todas las queries de filtran por fecha_local ( — `ReporteService.java:56, 76, 112, 130, 146, 154, 220` — idx_ventas_fecha_local faltante — todas las queries de filtran por fecha_local (columna generada) y el único índice es sobre fecha (timestamp) que el planner **no puede usar** para el predicado generado
  - **Acción**: CREATE INDEX idx_ventas_fecha_local ON ven.ventas(fecha_local) WHERE estado='COMPLETADA'; y equivalentes _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-014` PgBouncer HPA × max_db_connections sobre-fija el pool — `00-base.yaml:32` — PgBouncer HPA × max_db_connections sobre-fija el pool. max_connections=300 () vs max_db_connections=120 × maxReplicas=6 ⇒ 720 slots vs 300 PG. Cap HPA maxReplicas=3
  - **Acción**: bajar max_db_connections a 50 o subir PG max_connections a 600 _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-015` OTel sampling al 100 % — `docker-compose.yml:118-127` — OTel sampling al 100 %. no define OTEL_TRACES_SAMPLER → parentbased_always_on. POS registra cada venta con 3-5 spans; catálogo 2 spans. A 100 RPS → 300-500 spans/s. Añadir tail_sampling processor para errores al 100 %
  - **Acción**: OTEL_TRACES_SAMPLER=parentbased_traceidratio + OTEL_TRACES_SAMPLER_ARG=0.05 _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-016` OTel cardinality bomb — `otel-collector.yaml:71-80` · `ferreteriaFront/src/telemetry/otel.ts:59` — OTel cardinality bomb. con resource_to_telemetry_conversion: enabled: true + browser.user_agent desde ⇒ una serie nueva por combinación browser/version en Prometheus
  - **Acción**: strip process.* + browser.user_agent antes del exporter Prometheus _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-017` checkout llama em — `ven/service/VentaService.java:107-115` — checkout llama em.refresh(savedVenta) y luego em.refresh por cada detalle. 51 SELECT para venta de 50 SKUs
  - **Acción**: RETURNING clause desde el INSERT, o un único SELECT proyectado a DTO _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-018` DEFAULT_MAX_SIZE = 500 se aplica a TODOS los endpoints — `common/web/PageQuery.java:32-46` — DEFAULT_MAX_SIZE = 500 se aplica a TODOS los endpoints. Una página de 500 con joins + lookups batch sigue siendo pesada
  - **Acción**: bajar default a 100 y permitir override por endpoint _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-019` findByFechaBetweenOrderByFechaDesc carga entidad completa (notas, metodoPagoSat, — `ven/repo/VentaRepository.java:11-14` — findByFechaBetweenOrderByFechaDesc carga entidad completa (notas, metodoPagoSat, folioFiscalUuid) cuando los listados solo muestran total/subtotal/folio/fecha
  - **Acción**: proyecciones _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-020` Caffeine local con maximumSize=200_000 por instancia — `common/web/RateLimitInterceptor.java:57-71` — Caffeine local con maximumSize=200_000 por instancia. Memoria O(N) por pod, no compartido horizontalmente
  - **Acción**: bucket4j-redis o Hazelcast _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-021` page — `common/web/EnvelopeAdvice.java:50-58` — page.map(p -> toResponse(...)) se ejecuta por elemento. Micro-allocaciones en hot-path
  - **Acción**: Map.of(...) inmutable _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-022` LOG — `common/web/RequestIdFilter.java:51` — LOG.info(uri={} incoming={}) corre en CADA request
  - **Acción**: DEBUG _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-023` sin índices secundarios para stock < stock_minimo; findBajoStock hace full scan — `inv/entity/Inventario.java:13-23`
  - **Acción**: índice parcial WHERE stock <= stock_minimo _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-024` X-Forwarded-For se confía sin validar proxy — `common/web/RateLimitInterceptor.java:43-55` — X-Forwarded-For se confía sin validar proxy.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-025` claveDe lee de SecurityContextHolder (ThreadLocal); no aplica en reactivo/async — `common/web/RateLimitInterceptor.java:118-126` — claveDe lee de SecurityContextHolder (ThreadLocal); no aplica en reactivo/async.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-026` whitelist de paths frágil (lista negra por prefijo) — `common/web/EnvelopeAdvice.java:42-66` — whitelist de paths frágil (lista negra por prefijo).
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-027` findByCategoriaCategoriaIdAndActivoTrue carga entidad completa; usar proyeccione — `cat/repo/ProductoRepository.java:11-17` — findByCategoriaCategoriaIdAndActivoTrue carga entidad completa; usar proyecciones.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-003` triggers AFTER INSERT per-row sobre tablas calientes — `scripts/02_tablas.sql:1218-1242, 1330-1420, 1678-1780` — triggers AFTER INSERT per-row sobre tablas calientes. Bulk insert 100× más lento que bulk copy
  - **Acción**: documentar bypass con COPY … WITH (FREEZE) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-004` PgBouncer pool_mode=transaction sin read/write split — `deploy/docker-compose.yml:393-446`
  - **Acción**: PgBouncer secundario session mode para reports o routing por user _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-005` vistas analíticas con GROUP BY date_trunc( — `scripts/vistas_core.sql:9-23, 218-240, 243-260, 262-272` — vistas analíticas con GROUP BY date_trunc(...), RANK() OVER (…)
  - **Acción**: MATERIALIZED VIEW CONCURRENTLY (PG 14+) + índice único _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-006` sequences globales (inv — `scripts/02_tablas.sql:248, 635, 731` — sequences globales (inv.productos, ven.ventas, ven.cuentas_cobrar) son predecibles y WAL hotspot
  - **Acción**: UUIDv7 (pg_uuidv7) para ven.ventas _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-007` inv — `scripts/02_tablas.sql:265, 268` — inv.productos.atributos JSONB sin compresión TOAST; 10 KB × 10 k productos = 100 MB
  - **Acción**: CHECK (length(atributos::text) < 4096) o EAV _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-008` volumen pg_data_primary sin pgBackRest/WAL-G para PITR — `deploy/docker-compose.yml:325-336`
  - **Acción**: pgBackRest stanza=ferreteria repo1-path=/backups (7d/4w/12m) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-009` PVC sin StorageClass específico — `deploy/docker-compose.yml:309-336,` · `deploy/k8s/10-postgres.yaml:113-120`
  - **Acción**: io2 para OLTP _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-010` pg_stat_statements — `deploy/conf/postgresql.conf:66-70` — pg_stat_statements.max = 10000 se rota rápido
  - **Acción**: subir a 50000 _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-001` fin — `scripts/02_tablas.sql:1725-1739` — fin.fn_cerrar_turno ejecuta 4 jsonb_object_agg sobre TODO el set de movimientos del turno en cada cierre
  - **Acción**: materializar resumen en fin.movimientos_caja + índice (turno_caja_id, concepto, tipo) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-002` com — `scripts/02_tablas.sql:1481-1491` — com.fn_detalle_compra_entrada recalcula stock previo con SUM sobre TODO inv.movimientos_inventario filtrado por producto en cada línea → O(n²) en compras grandes
  - **Acción**: leer stock de inv.inventario directamente + WPCC incremental _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-003` ven — `scripts/02_tablas.sql:1330-1420` — ven.fn_recalc_totales_venta se dispara AFTER INSERT OR DELETE por fila → N recálculos por venta de N líneas
  - **Acción**: cambiar a AFTER … FOR EACH STATEMENT con pg_trigger_depth() _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-004` inv — `scripts/02_tablas.sql:300-330` — inv.inventario PK (producto_id, almacen_id) con stock actualizado por trigger AFTER INSERT ⇒ contención en SKUs alta rotación.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `FRONT-REND-001` 6 listeners (mousemove, mousedown, keydown, scroll, touchstart, click) sin throt — `src/hooks/useInactivityTimeout.ts:41-55` — 6 listeners (mousemove, mousedown, keydown, scroll, touchstart, click) sin throttle/debounce. mousemove puede dispararse >100/seg → cada llamada actualiza useAuthStore.lastActivityAt ⇒ re-render de todos los selectores suscritos
  - **Acción**: throttle 1s o timestamp local _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `FRONT-REND-002` 50+ lazy() con <Suspense fallback={spinners — `src/router/router.tsx:104-520` — 50+ lazy() con <Suspense fallback={spinners.full}> ⇒ solo spinner global. Sin esqueletos contextuales por ruta
  - **Acción**: Suspense por feature _(blast_radius: `perf`; confidence: `HIGH`)_

### Regresión

- **CRITICAL** `FRONT-MAN-001` [CR #7] — **sin test script, sin vitest/jest, sin @testing-library** — `package.json:6-11` — [CR #7] — **sin test script, sin vitest/jest, sin @testing-library**. Búsqueda confirma **0 archivos *.test.*/*.spec.*** en src/. Riesgo crítico en POS/finanzas.
  - **Acción**: (definir acción concreta) _(blast_radius: `regression`; confidence: `HIGH`)_

### Seguridad

- **CRITICAL** `BACK-SEC-002` JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc= — `application.yml:43` — JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc=. Si falta env var, arranca con secreto commiteado
  - **Acción**: quitar default; @PostConstruct fail-fast _(blast_radius: `security`; confidence: `HIGH`)_

- **CRITICAL** `DB-SEC-001` CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION' — `scripts/01_base_esquemas.sql:29` — CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION'. Si se ejecuta tal cual, login con password trivial
  - **Acción**: ALTER ROLE … WITH PASSWORD NULL _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-003` + ferreteriaDB/deploy/ — `ferreteriaDB/scripts/01_base_esquemas.sql:29` · `k8s/00-base.yaml:19-21` — + ferreteriaDB/deploy/.env:18,20 + — contraseñas DB por default en plaintext, en stringData K8s
  - **Acción**: External Secrets / Vault / Sealed Secrets _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-004` bootstrap admin admin / Admin123* (bcrypt 12) — `ferreteriaDB/scripts/04_admin.sql:51` — bootstrap admin admin / Admin123* (bcrypt 12). El comentario dice "CAMBIAR en primer login" pero no hay código que fuerce password_change_required
  - **Acción**: trigger backend que fuerce cambio en primer login _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-005` /actuator/** y Swagger UI son permitAll — `common/config/SecurityConfig.java:92-95` — /actuator/** y Swagger UI son permitAll. /actuator/prometheus enumera la API entera
  - **Acción**: lock down a ADMINISTRADOR o mover a puerto interno _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-006` ACCESS_MINUTES default 480 (8 h) — `application.yml:44-45` — ACCESS_MINUTES default 480 (8 h). Para un POS con datos financieros debería ser 15 min
  - **Acción**: bajar a 15 min, refresh rotativo _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-007` /api/v1/auth/refresh y /logout sin @RateLimited — `seg/api/AuthController.java:85-97` — /api/v1/auth/refresh y /logout sin @RateLimited. Un atacante con un refresh token puede emitir access tokens sin límite
  - **Acción**: rate-limit ambos _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-008` ninguna cabecera de seguridad (X-Content-Type-Options, X-Frame-Options, HSTS, Re — `common/config/SecurityConfig.java:48-91` — ninguna cabecera de seguridad (X-Content-Type-Options, X-Frame-Options, HSTS, Referrer-Policy, CSP)
  - **Acción**: headers(...) en SecurityFilterChain _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-009` ?sort= sin whitelist — `common/web/PageQuery.java:34-50` · `MovimientoInventarioRepository.java:22-49` — ?sort= sin whitelist. Combinado con queries nativas en permite ?sort=(SELECT pg_sleep(10)) o columnas inexistentes (stack trace → 500)
  - **Acción**: whitelist por controller _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-010` changePassword NO invalida los refresh tokens ya emitidos — `seg/service/AuthService.java:111-116` — changePassword NO invalida los refresh tokens ya emitidos. Atacante puede seguir usando access tokens hasta expiración (8 h)
  - **Acción**: gateway.revokeAllRefreshTokens(user.usuarioId()) post cambio _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-011` CORS_ALLOW_CREDENTIALS=true + setAllowedOriginPatterns(["*"]) cuando origins con — `common/web/CorsConfigurationFactory.java:36-50` — CORS_ALLOW_CREDENTIALS=true + setAllowedOriginPatterns(["*"]) cuando origins contiene "*". Combinación prohibida; Spring lo permite pero anula la seguridad
  - **Acción**: validar al arranque _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-012` handleDataAccess loguea ex — `common/error/GlobalExceptionHandler.java:48-55` — handleDataAccess loguea ex.getMostSpecificCause().getMessage() en WARN/ERROR. Los mensajes de PostgreSQL incluyen esquema/columnas/valores
  - **Acción**: loguear solo SQLSTATE _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-002` local all all trust permite acceso sin auth dentro del contenedor — `deploy/conf/pg_hba.conf:2`
  - **Acción**: peer o scram-sha-256 _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-004` solo inv — `scripts/02_tablas.sql:1918-1919` — solo inv.movimientos_inventario, fin.movimientos_caja, seg.auditoria tienen REVOKE DELETE. ven.ventas, ven.cuentas_cobrar, com.compras, com.cuentas_pagar, fin.cortes_caja quedan con DELETE habilitado
  - **Acción**: REVOKE DELETE en todas las ledger; cancelaciones vía UPDATE de estado _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-005` ninguna función tiene SET search_path ni SECURITY DEFINER → **search_path hijack — `scripts/02_tablas.sql:1118, 1218-1242, 1330-1420, 1678-1780` — ninguna función tiene SET search_path ni SECURITY DEFINER → **search_path hijack** risk
  - **Acción**: añadir SET search_path = pg_catalog, public, … en cada CREATE OR REPLACE FUNCTION _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-006` seg — `scripts/02_tablas.sql:208-220` — seg.auditoria almacena password_hash (bcrypt) en datos_anteriores/datos_nuevos (via to_jsonb(NEW)). Cualquier SELECT expone el hash
  - **Acción**: WHEN clause que excluya password_hash, o enmascarar _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-007` host … 0 — `deploy/conf/pg_hba.conf:4-5` — host … 0.0.0.0/0 … scram-sha-256 y ::/0 aceptan la app desde cualquier IP
  - **Acción**: restringir a 10.0.0.0/8 _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `FRONT-SEC-001` clasificación CSRF **demasiado amplia**: cualquier 403 mutating se reintenta com — `src/lib/api/client.ts:173-189` — clasificación CSRF **demasiado amplia**: cualquier 403 mutating se reintenta como CSRF. Un 403 legítimo (permisos) puede enmascararse como éxito
  - **Acción**: diferenciar por error.response.data?.codigo === 'CSRF_TOKEN_INVALID' _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `FRONT-SEC-002` + — flag autenticado en localStorage como única verdad; no hay GET /auth/me en b — `src/store/auth.ts:22-58` · `src/router/guards.tsx:8-15` — + — flag autenticado en localStorage como única verdad; no hay GET /auth/me en boot. UI puede mentir sobre estado de sesión
  - **Acción**: revalidar contra backend en bootstrap _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `FRONT-SEC-003` sin CSP, HSTS, COOP, CORP — `nginx.conf:113-120` — sin CSP, HSTS, COOP, CORP. XSS defense 100% en React
  - **Acción**: CSP estricta _(blast_radius: `security`; confidence: `HIGH`)_

---

## §3 Por proyecto × dimensión

### 3.1 ferreteriaBackend

**Stack canónico** (de `audits/stacks.yaml`):

- **Runtime**: Spring Boot 3.3.4
- **Language**: Java 21 (toolchain)
- **Build**: Gradle 8.10.2 (wrapper)
- **Otros**: `persistence`=JPA/Hibernate, Flyway, PostgreSQL JDBC, `security`=Spring Security 6, JJWT 0.12.6, `observability`=OpenTelemetry SDK 1.43.0, OTel Java agent 2.10.0, Micrometer/Prometheus, `test`=JUnit 5, ArchUnit 1.3.0, Testcontainers, JaCoCo (gate >=80%), `ancillary`=Bucket4j 8.10.1, Caffeine, Lombok, springdoc-openapi 2.6.0

#### 3.1.1 Rendimiento

**CRITICAL** (12)

- **CRITICAL** `BACK-REND-001` toResponse ejecuta clienteRepo — `ven/service/VentaService.java:165-204` — toResponse ejecuta clienteRepo.findById + almacenRepo.findById + formaPagoRepo.findById + detalleRepo.findByVentaId + por cada detalle productoRepo.findById + cuentaRepo.findByVentaId + pagoRepo.findByCuentaCobrarIdOrderByFechaDesc — N+1 g…
  - **Acción**: findAllById(...) batch + Map<Long,T> en mapper _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-002` mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle — `com/service/CompraService.java:198-217` — mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-003` mismo patrón — `ven/service/CotizacionService.java:120-138` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-004` toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 — `ven/service/CreditoService.java:43-63` — toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 severo en /api/v1/creditos/cobranza.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-005` mismo patrón — `ven/service/DevolucionService.java:66-83` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-006` mismo patrón — `ven/service/RentaService.java:109-122` — mismo patrón.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-007` listCajas/listTurnos/toCorteResponse iteran almacenRepo — `fin/service/CajaService.java:96-101, 244-262` — listCajas/listTurnos/toCorteResponse iteran almacenRepo.findById y cajaRepo.findById por cada elemento.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-008` listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway — `seg/service/SegAdminService.java:69-75, 121-126, 165-170` — listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway.findX(limit, offset) + lookups en map() (N+1). Paginación manual con offset = page * size que degrada con páginas altas.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-009` paginación manual con gateway — `seg/service/AuditoriaService.java:36-48` — paginación manual con gateway.buscar() + gateway.contar() separado; OFFSET degrada con filtros ILIKE.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-010` generarQuincena y pagarLote hacen jdbc — `rh/service/NominaService.java:94-100, 137-141, 178-184` — generarQuincena y pagarLote hacen jdbc.queryForObject por cada fila (COUNT(*) duplicado, nombre_completo empleado). N+1.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-011` list invoca detalleRepo — `inv/service/TrasladoService.java:43-50, 132-145` — list invoca detalleRepo.findByTrasladoId dentro de page.map (N+1); create hace 2 jdbc.queryForObject para candidatos por detalle.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `BACK-REND-012` cuando llega almacenId, hace inventarioRepo — `cat/service/ProductoService.java:38-69` — cuando llega almacenId, hace inventarioRepo.findByAlmacenIdAndProductoId por cada producto de la página.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**HIGH** (15)

- **HIGH** `BACK-REND-013` idx_ventas_fecha_local faltante — todas las queries de filtran por fecha_local ( — `ReporteService.java:56, 76, 112, 130, 146, 154, 220` — idx_ventas_fecha_local faltante — todas las queries de filtran por fecha_local (columna generada) y el único índice es sobre fecha (timestamp) que el planner **no puede usar** para el predicado generado
  - **Acción**: CREATE INDEX idx_ventas_fecha_local ON ven.ventas(fecha_local) WHERE estado='COMPLETADA'; y equivalentes _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-014` PgBouncer HPA × max_db_connections sobre-fija el pool — `00-base.yaml:32` — PgBouncer HPA × max_db_connections sobre-fija el pool. max_connections=300 () vs max_db_connections=120 × maxReplicas=6 ⇒ 720 slots vs 300 PG. Cap HPA maxReplicas=3
  - **Acción**: bajar max_db_connections a 50 o subir PG max_connections a 600 _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-015` OTel sampling al 100 % — `docker-compose.yml:118-127` — OTel sampling al 100 %. no define OTEL_TRACES_SAMPLER → parentbased_always_on. POS registra cada venta con 3-5 spans; catálogo 2 spans. A 100 RPS → 300-500 spans/s. Añadir tail_sampling processor para errores al 100 %
  - **Acción**: OTEL_TRACES_SAMPLER=parentbased_traceidratio + OTEL_TRACES_SAMPLER_ARG=0.05 _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-016` OTel cardinality bomb — `otel-collector.yaml:71-80` · `ferreteriaFront/src/telemetry/otel.ts:59` — OTel cardinality bomb. con resource_to_telemetry_conversion: enabled: true + browser.user_agent desde ⇒ una serie nueva por combinación browser/version en Prometheus
  - **Acción**: strip process.* + browser.user_agent antes del exporter Prometheus _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-017` checkout llama em — `ven/service/VentaService.java:107-115` — checkout llama em.refresh(savedVenta) y luego em.refresh por cada detalle. 51 SELECT para venta de 50 SKUs
  - **Acción**: RETURNING clause desde el INSERT, o un único SELECT proyectado a DTO _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-018` DEFAULT_MAX_SIZE = 500 se aplica a TODOS los endpoints — `common/web/PageQuery.java:32-46` — DEFAULT_MAX_SIZE = 500 se aplica a TODOS los endpoints. Una página de 500 con joins + lookups batch sigue siendo pesada
  - **Acción**: bajar default a 100 y permitir override por endpoint _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-019` findByFechaBetweenOrderByFechaDesc carga entidad completa (notas, metodoPagoSat, — `ven/repo/VentaRepository.java:11-14` — findByFechaBetweenOrderByFechaDesc carga entidad completa (notas, metodoPagoSat, folioFiscalUuid) cuando los listados solo muestran total/subtotal/folio/fecha
  - **Acción**: proyecciones _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-020` Caffeine local con maximumSize=200_000 por instancia — `common/web/RateLimitInterceptor.java:57-71` — Caffeine local con maximumSize=200_000 por instancia. Memoria O(N) por pod, no compartido horizontalmente
  - **Acción**: bucket4j-redis o Hazelcast _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-021` page — `common/web/EnvelopeAdvice.java:50-58` — page.map(p -> toResponse(...)) se ejecuta por elemento. Micro-allocaciones en hot-path
  - **Acción**: Map.of(...) inmutable _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-022` LOG — `common/web/RequestIdFilter.java:51` — LOG.info(uri={} incoming={}) corre en CADA request
  - **Acción**: DEBUG _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-023` sin índices secundarios para stock < stock_minimo; findBajoStock hace full scan — `inv/entity/Inventario.java:13-23`
  - **Acción**: índice parcial WHERE stock <= stock_minimo _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-024` X-Forwarded-For se confía sin validar proxy — `common/web/RateLimitInterceptor.java:43-55` — X-Forwarded-For se confía sin validar proxy.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-025` claveDe lee de SecurityContextHolder (ThreadLocal); no aplica en reactivo/async — `common/web/RateLimitInterceptor.java:118-126` — claveDe lee de SecurityContextHolder (ThreadLocal); no aplica en reactivo/async.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-026` whitelist de paths frágil (lista negra por prefijo) — `common/web/EnvelopeAdvice.java:42-66` — whitelist de paths frágil (lista negra por prefijo).
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-REND-027` findByCategoriaCategoriaIdAndActivoTrue carga entidad completa; usar proyeccione — `cat/repo/ProductoRepository.java:11-17` — findByCategoriaCategoriaIdAndActivoTrue carga entidad completa; usar proyecciones.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.1.2 Seguridad

**CRITICAL** (2)

- **CRITICAL** `BACK-SEC-001` cat/api/ConfiguracionController — `02_tablas.sql:1229-1233` — cat/api/ConfiguracionController.java + ConfiguracionService.java — PUT /api/v1/configuraciones/{clave} deja a cualquier usuario autenticado flipear cfg.configuracion.permitir_stock_negativo (verificado por )
  - **Acción**: restringir a ADMINISTRADOR _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **CRITICAL** `BACK-SEC-002` JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc= — `application.yml:43` — JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc=. Si falta env var, arranca con secreto commiteado
  - **Acción**: quitar default; @PostConstruct fail-fast _(blast_radius: `security`; confidence: `HIGH`)_

**HIGH** (10)

- **HIGH** `BACK-SEC-003` + ferreteriaDB/deploy/ — `ferreteriaDB/scripts/01_base_esquemas.sql:29` · `k8s/00-base.yaml:19-21` — + ferreteriaDB/deploy/.env:18,20 + — contraseñas DB por default en plaintext, en stringData K8s
  - **Acción**: External Secrets / Vault / Sealed Secrets _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-004` bootstrap admin admin / Admin123* (bcrypt 12) — `ferreteriaDB/scripts/04_admin.sql:51` — bootstrap admin admin / Admin123* (bcrypt 12). El comentario dice "CAMBIAR en primer login" pero no hay código que fuerce password_change_required
  - **Acción**: trigger backend que fuerce cambio en primer login _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-005` /actuator/** y Swagger UI son permitAll — `common/config/SecurityConfig.java:92-95` — /actuator/** y Swagger UI son permitAll. /actuator/prometheus enumera la API entera
  - **Acción**: lock down a ADMINISTRADOR o mover a puerto interno _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-006` ACCESS_MINUTES default 480 (8 h) — `application.yml:44-45` — ACCESS_MINUTES default 480 (8 h). Para un POS con datos financieros debería ser 15 min
  - **Acción**: bajar a 15 min, refresh rotativo _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-007` /api/v1/auth/refresh y /logout sin @RateLimited — `seg/api/AuthController.java:85-97` — /api/v1/auth/refresh y /logout sin @RateLimited. Un atacante con un refresh token puede emitir access tokens sin límite
  - **Acción**: rate-limit ambos _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-008` ninguna cabecera de seguridad (X-Content-Type-Options, X-Frame-Options, HSTS, Re — `common/config/SecurityConfig.java:48-91` — ninguna cabecera de seguridad (X-Content-Type-Options, X-Frame-Options, HSTS, Referrer-Policy, CSP)
  - **Acción**: headers(...) en SecurityFilterChain _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-009` ?sort= sin whitelist — `common/web/PageQuery.java:34-50` · `MovimientoInventarioRepository.java:22-49` — ?sort= sin whitelist. Combinado con queries nativas en permite ?sort=(SELECT pg_sleep(10)) o columnas inexistentes (stack trace → 500)
  - **Acción**: whitelist por controller _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-010` changePassword NO invalida los refresh tokens ya emitidos — `seg/service/AuthService.java:111-116` — changePassword NO invalida los refresh tokens ya emitidos. Atacante puede seguir usando access tokens hasta expiración (8 h)
  - **Acción**: gateway.revokeAllRefreshTokens(user.usuarioId()) post cambio _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-011` CORS_ALLOW_CREDENTIALS=true + setAllowedOriginPatterns(["*"]) cuando origins con — `common/web/CorsConfigurationFactory.java:36-50` — CORS_ALLOW_CREDENTIALS=true + setAllowedOriginPatterns(["*"]) cuando origins contiene "*". Combinación prohibida; Spring lo permite pero anula la seguridad
  - **Acción**: validar al arranque _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `BACK-SEC-012` handleDataAccess loguea ex — `common/error/GlobalExceptionHandler.java:48-55` — handleDataAccess loguea ex.getMostSpecificCause().getMessage() en WARN/ERROR. Los mensajes de PostgreSQL incluyen esquema/columnas/valores
  - **Acción**: loguear solo SQLSTATE _(blast_radius: `security`; confidence: `HIGH`)_

**MEDIUM** (24)

- **MEDIUM** `BACK-SEC-013` CSRF se ignora para /auth/login y /auth/register — `common/config/SecurityConfig.java:67-72` — CSRF se ignora para /auth/login y /auth/register. Resto requiere header X-XSRF-TOKEN cuando se usan cookies. Clientes no-browser (Postman/curl/mobile) deben llamar /csrf-init primero. Documentar.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-014` CSRF con CookieCsrfTokenRepository — `common/config/SecurityConfig.java:48-91` — CSRF con CookieCsrfTokenRepository.withHttpOnlyFalse(); cookie legible por JS. Cualquier XSS roba el token
  - **Acción**: CSP estricta en front _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-015` JwtException se loguea con ex — `common/security/JwtAuthFilter.java:54-77` — JwtException se loguea con ex.getMessage() (puede incluir posición del token ofuscado)
  - **Acción**: loguear getClass().getSimpleName() _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-016` reemplazarRoles/reemplazarPermisos borran antes de insertar sin SERIALIZABLE — `seg/repo/SegAdminRepository.java:97-105` — reemplazarRoles/reemplazarPermisos borran antes de insertar sin SERIALIZABLE. Concurrencia puede borrar asignaciones del otra
  - **Acción**: Isolation.SERIALIZABLE o UPSERT _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-017` ipCliente confía en X-Forwarded-For — `common/web/RateLimitInterceptor.java:43-55` — ipCliente confía en X-Forwarded-For. Sin server.forward-headers-strategy=framework el atacante puede spoofear IP y evadir rate-limit.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-018` /api/v1/catalogos lista todos los catálogos a cualquier autenticado — `cat/api/CatalogoController.java:35-43` — /api/v1/catalogos lista todos los catálogos a cualquier autenticado. Information disclosure pasiva.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-019` /actuator/metrics y /actuator/info sin restricción — `application.yml:54-58` — /actuator/metrics y /actuator/info sin restricción. Fingerprinting
  - **Acción**: reducir a health,prometheus _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-020` + — AUTH_COOKIE_SECURE=false por default — `application.yml:60-66` · `AuthCookieProperties.java:27-28` — + — AUTH_COOKIE_SECURE=false por default. Solo .env.example lo cambia. Si el deploy olvida, cookies sobre HTTP
  - **Acción**: fail-closed fuera de dev profile _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-021` + — CORS allow-credentials=true coexiste con allowed-headers=* — `application.yml:51-58` · `CorsConfigurationFactory.java:30-52` — + — CORS allow-credentials=true coexiste con allowed-headers=*. Default distinto entre application.yml (false) y docker .env (true). Inconsistencia.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-022` username enumeration via timing — `seg/service/AuthService.java:100-106` — username enumeration via timing. findByUsername retorna inmediato en null, BCrypt 100 ms en existente
  - **Acción**: BCrypt dummy en user-not-found _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-023` ven/api/PagoClienteController — `CotizacionController.java:47-54` · `VentaController.java:46-50` — ven/api/PagoClienteController.java, , — IDOR; cualquier GET /api/v1/{recurso}/{id} acepta cualquier id sin ownership scoping. Combinado con S-01, lectura horizontal total.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-024` PATCH /ventas/{id}/cancelar sin role/almacen guard — `ven/api/VentaController.java:52-57` — PATCH /ventas/{id}/cancelar sin role/almacen guard. Crítico.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-025` + — REQUEST_ID_MODE=GENERATE por default; acepta X-Request-Id del cliente — `application.yml:39-40` · `RequestIdFilter.java:50-66` — + — REQUEST_ID_MODE=GENERATE por default; acepta X-Request-Id del cliente. Log poisoning
  - **Acción**: default STRICT _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-026` user input en MessageFormat template (riesgo {0}, ') — `fin/service/CajaService.java:178-181` — user input en MessageFormat template (riesgo {0}, '). Patrón repetido en NominaService, CiudadService, EstadoService, ImpuestoService, MotivoMovimientoService, TipoGastoService, TasaImpuestoService
  - **Acción**: escapar metacaracteres o sanitizar en DTO _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-027` sin server — `application.yml:34-37` — sin server.tomcat.max-http-form-post-size. Default 2 MB; nginx permite 25 MB
  - **Acción**: 1 MB _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-028` + — errores hacen echo de instance (URI) y requestId — `application.yml:85-105` · `GlobalExceptionHandler.java:47-60` — + — errores hacen echo de instance (URI) y requestId. Acceptable para uso interno; revisar si logs salen a terceros.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-029` User-Agent persistido en seg — `seg/service/AuthService.java:108-113` — User-Agent persistido en seg.sesiones; fingerprinting
  - **Acción**: truncar/opt-in _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-030` + — UserPrincipal lleva roles del JWT — `seg/service/AuthService.java:33-36` · `JwtAuthFilter.java:64-74` — + — UserPrincipal lleva roles del JWT. Cambio de rol no aplica hasta expiración (8 h)
  - **Acción**: reload de DB o TTL más corto _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-031` K8s Secret con stringData (plaintext) y committed — `k8s/00-base.yaml:19-21`
  - **Acción**: External Secrets / Sealed Secrets / Vault _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-032` OTel browser SDK habilitado por default en Docker — `ferreteriaFront/src/telemetry/otel.ts:62-94` — OTel browser SDK habilitado por default en Docker. Envía URLs de API (incluyendo /auth/me, /api/v1/clientes/{id}, /api/v1/ventas/{id}) a localhost:4318. Sin consent. LFPDPPP / GDPR issue
  - **Acción**: opt-in flag, banner, redactar Authorization/cookies _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-033` + — single HMAC shared entre access y refresh — `application.yml:43` · `JwtService.java:41-46` — + — single HMAC shared entre access y refresh. Aceptable pero documentar y considerar RS256 para access.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-034` sin password-policy enforcement en register — `AuthDtos.java:19-27` — sin password-policy enforcement en register. Bcrypt cost 10 (no 12)
  - **Acción**: complexity + bump cost _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-035` userlist — `docker-compose.yml:432-433` — userlist.txt con printf; passwords con $, \, ", newline rompen
  - **Acción**: htpasswd -nbB o escape explícito _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `BACK-SEC-036` + — User-Agent persistido — `AuthService.java:108-113` · `AuthRepository.java:119-129` — + — User-Agent persistido.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

**LOW** (8)

- **LOW** `BACK-SEC-037` single SecretKey sin soporte de rotación (ver CRITICAL #5) — `JwtService.java:36-49` — single SecretKey sin soporte de rotación (ver CRITICAL #5).
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-038` sin MERGE/UPSERT — `seg/repo/SegAdminRepository.java:97-105` — sin MERGE/UPSERT.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-039` + — /auth/me devuelve email, telefono del empleado a cualquier usuario autentica — `JwtService.java:51-61` · `AuthController.java:123-132` — + — /auth/me devuelve email, telefono del empleado a cualquier usuario autenticado (combinado con S-01).
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-040` imágenes con tags flotantes (pgbouncer/pgbouncer:latest) — `docker-compose.yml:149,176,215,248,282,310` — imágenes con tags flotantes (pgbouncer/pgbouncer:latest).
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-041` sin readOnlyRootFilesystem, allowPrivilegeEscalation: false, capabilities — `k8s/10-postgres.yaml:55-59` — sin readOnlyRootFilesystem, allowPrivilegeEscalation: false, capabilities.drop: [ALL].
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-042` sin securityContext — `k8s/20-pgbouncer.yaml:25-72` — sin securityContext.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-043` bucket por FQN controller; doc del burst — `RateLimitInterceptor.java:130-138` — bucket por FQN controller; doc del burst.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `BACK-SEC-044` TokenResponse — `auth/dto/AuthDtos.java:51-58` — TokenResponse.refreshToken puede ser null; verificar serialización.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

#### 3.1.3 Diseño

**HIGH** (2)

- **HIGH** `BACK-DIS-001` solo 2 reglas ArchUnit activas — `architecture/MensajesSoloDesdeErrorCodeTest.java:36-47` — solo 2 reglas ArchUnit activas. Faltan: @Service solo en *Service, @RestController solo en *Controller, gateways solo consumidos por service/, repositories no dependen de controllers, services no inyectan repos de otro módulo, DTOs no usan…
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-DIS-002` @PersistenceContext EntityManager dentro de service (detalle de infra en service — `ven/service/VentaService.java:53-54` — @PersistenceContext EntityManager dentro de service (detalle de infra en service). Rompe hexagonal/layered
  - **Acción**: extraer VentaWriteRepository _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (3)

- **MEDIUM** `BACK-DIS-003` envelope {success, data, codigo, errorMessage, requestId, instance} no cumple RF — `common/error/GlobalExceptionHandler.java:67-79` — envelope {success, data, codigo, errorMessage, requestId, instance} no cumple RFC 7807 (type, title, status, detail, instance)
  - **Acción**: emitir ambos formatos o migrar a application/problem+json bajo /api/v2 _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-DIS-004` mezcla JPA repositories + JdbcTemplate + EntityManager + funciones PostgreSQL — `ven/service/VentaService.java:9-12` — mezcla JPA repositories + JdbcTemplate + EntityManager + funciones PostgreSQL. Acopla la API al esquema y dificulta testing sin Testcontainers
  - **Acción**: aislar tras un gateway _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-DIS-005` advice intercepta TODAS las respuestas; lista negra por path — `common/web/EnvelopeAdvice.java:42-66` — advice intercepta TODAS las respuestas; lista negra por path.startsWith(...) es frágil
  - **Acción**: whitelist _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (2)

- **LOW** `BACK-DIS-006` service → service → repo cross-module; documentar con ADR — `rh/service/EmpleadoService.java:36-43` — service → service → repo cross-module; documentar con ADR.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-DIS-007` loguea URI en cada request; revisar si filtra IDs/rutas internas — `common/web/RequestIdFilter.java:51` — loguea URI en cada request; revisar si filtra IDs/rutas internas.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.1.4 Estabilidad

**HIGH** (4)

- **HIGH** `BACK-EST-001` catch (Exception e) { omitidas++; } traga TODA excepción sin loguear — `rh/service/NominaService.java:118-124` — catch (Exception e) { omitidas++; } traga TODA excepción sin loguear. Imposible diagnosticar nóminas faltantes
  - **Acción**: LOG.warn _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-002` checkout ejecuta ventaRepo — `ven/service/VentaService.java:128-138` — checkout ejecuta ventaRepo.flush() + em.refresh(savedVenta) + em.refresh(detalle) por cada detalle. Errores no-RuntimeException pueden dejar sesión JPA rota
  - **Acción**: usar RETURNING en lugar de refresh _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-003` refresh() con dos refresh concurrentes del mismo cliente (mismo hash) provoca TO — `seg/service/AuthService.java:141-219` — refresh() con dos refresh concurrentes del mismo cliente (mismo hash) provoca TOKEN_EXPIRADO
  - **Acción**: SELECT FOR UPDATE sobre seg.refresh_tokens.token_hash _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-EST-004` create recorre req — `inv/service/TrasladoService.java:90-110` — create recorre req.detalles() con productoRepo.findById (1 query por producto) + 2 MovimientoInventario.save por detalle. Para 100 SKUs = 200 inserts sin flush periódico
  - **Acción**: em.flush() cada N inserts _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (7)

- **MEDIUM** `BACK-EST-005` 9 dependencias inyectadas — `ven/service/VentaService.java:51-52` — 9 dependencias inyectadas. SRP; extraer VentaFinanzas de VentaCheckout.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-006` cerrarTurno llama fn_cerrar_turno( — `fin/service/CajaService.java:159-161` — cerrarTurno llama fn_cerrar_turno(...); si falla, no se loguea motivo (caja sin ventas, diferencia excesiva)
  - **Acción**: mapear SQLSTATE a ErrorCode con DbErrorTranslator.translate _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-007` UUID — `common/web/RequestIdFilter.java:62-69` — UUID.fromString(incoming) lanza IllegalArgumentException capturado localmente. Pero LOG.info línea 51 corre ANTES de la validación, así que cliente con header inválido genera 2 logs.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-008` changePassword no invalida sesiones concurrentes (mismo usuario en 2 dispositivo — `seg/service/AuthService.java:81-91` — changePassword no invalida sesiones concurrentes (mismo usuario en 2 dispositivos).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-009` translate recorre 15 niveles de causa; puede perder SQLException raíz en wrap pr — `common/error/DbErrorTranslator.java:39-50` — translate recorre 15 niveles de causa; puede perder SQLException raíz en wrap profundo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-010` create consulta inventarioRepo — `inv/service/ConteoFisicoService.java:48-66` — create consulta inventarioRepo.findById(invId) para cada detalle; si un producto no tiene fila, cantidadSistema=0 silencioso
  - **Acción**: validar todos antes de continuar _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-011` /actuator/metrics y /actuator/info abiertos sin restricción — `application.yml:54-58` — /actuator/metrics y /actuator/info abiertos sin restricción.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (4)

- **LOW** `BACK-EST-012` LOG — `common/web/RequestIdFilter.java:51` — LOG.info hot-path. Cambiar a DEBUG.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-EST-013` em — `ven/service/VentaService.java:53-54` — em.refresh debería usar LockModeType.NONE.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-EST-014` claveDe ThreadLocal no aplica en reactivo/async — `common/web/RateLimitInterceptor.java:118-126` — claveDe ThreadLocal no aplica en reactivo/async.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-EST-015` checkout sin @Async; considerar CQRS con eventos — `ven/service/VentaService.java:128-138` — checkout sin @Async; considerar CQRS con eventos.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.1.5 Escalabilidad

**HIGH** (5)

- **HIGH** `BACK-ESC-001` Caffeine local por instancia — `common/web/RateLimitInterceptor.java:43-71` — Caffeine local por instancia. Con N réplicas, límite efectivo N×capacidad
  - **Acción**: bucket4j-redis o Hazelcast _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-002` Hikari maximum-pool-size=10 sin auto-tuning ni connection timeout agresivo para — `application.yml:14-18` — Hikari maximum-pool-size=10 sin auto-tuning ni connection timeout agresivo para fallos. Documentar dimensionamiento para N cajas.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-003` + — catálogo promociones/descuentos se consulta cada venta — `ven/service/CotizacionService.java:51-58` · `PromocionService.java:65-87`
  - **Acción**: @Cacheable("promocionesActivas") TTL 60s _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-004` todos los catálogos sin cache — `cat/service/AbstractCatalogoService.java:30-35` — todos los catálogos sin cache. Endpoints de frontend (puestos, marcas, UMs, categorías, formas de pago, formas SAT, usos CFDI) golpean BD cada load
  - **Acción**: cache 5 min _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `BACK-ESC-005` cat/repo/ProductoRepository — `01_base_esquemas.sql:75-79` · `V1__base.sql:38` — cat/repo/ProductoRepository.java — búsqueda por texto en nombre/codigo sin índice trigram. La migración declara pg_trgm (línea comentada en ) — verificar índice GIN/trigram existe para inv.productos.codigo y nombre.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**MEDIUM** (2)

- **MEDIUM** `BACK-ESC-006` sin spring — `application.yml:73-77` — sin spring.jpa.properties.hibernate.generate_statistics=true + DataSource-Proxy.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `BACK-ESC-007` checkout no usa @Async — `ven/service/VentaService.java:101-110` — checkout no usa @Async. Para múltiples cajas simultáneas, considerar CQRS con eventos.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.1.7 UI/UX

**HIGH** (3)

- **HIGH** `BACK-UI-001` POST /auth/login retorna 200 (OK para sesión); POST /auth/register también 200 — — `seg/api/AuthController.java:46-53, 79-90` — POST /auth/login retorna 200 (OK para sesión); POST /auth/register también 200 — registro sí crea recurso, debería ser 201. Confirmar convención.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-UI-002` solo declara bearerJWT — `common/config/OpenApiConfig.java:18-27` — solo declara bearerJWT. La auth real es por cookies HttpOnly (at, rt). Swagger "Authorize" con Bearer <token> falla
  - **Acción**: esquema cookieAuth o documentar el flujo _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `BACK-UI-003` solo documenta success/data/meta para páginas — `common/web/EnvelopeAdvice.java:50-58` — solo documenta success/data/meta para páginas. Errores no documentados en OpenAPI.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (3)

- **MEDIUM** `BACK-UI-004` calcularTotales divide por 1 — `ven/service/CotizacionService.java:33-37` — calcularTotales divide por 1.16 con HALF_UP; BD puede usar otra convención. Documentar.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-UI-005` sort acepta cualquier propiedad sin whitelist — `common/web/PageQuery.java:15-44` — sort acepta cualquier propiedad sin whitelist.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-UI-006` handleDataAccess no incluye details con SQLSTATE — `common/error/GlobalExceptionHandler.java:48-55` — handleDataAccess no incluye details con SQLSTATE.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (4)

- **LOW** `BACK-UI-007` List<Catalogo> sin Page — `cat/catalogo/CatalogoController.java:24-28` — List<Catalogo> sin Page. Si crecen, response enorme.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-UI-008` LOG — `common/web/RequestIdFilter.java:48-51` — LOG.info cada request; afecta latencia percibida.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `BACK-UI-009` DEFAULT_MAX_SIZE=500 con N+1 latencia alta para /productos — `common/web/PageQuery.java:32-46` — DEFAULT_MAX_SIZE=500 con N+1 latencia alta para /productos.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `BACK-UI-010` /csrf-init devuelve 204 sin cuerpo — `seg/api/AuthController.java:113-118` — /csrf-init devuelve 204 sin cuerpo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.1.8 Mantenibilidad

**MEDIUM / LOW (resumen)** (10)

- **MEDIUM** `BACK-MAN-001` JaCoCo gate global >=80% excluye **/repo/** — `build.gradle.kts:155-178` — JaCoCo gate global >=80% excluye **/repo/**. Excluir repos en un Spring Data JPA oculta regresiones en queries nativas. Considerar >=70% con gate explícito.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-002` tres métodos list/listByFechaLocal/getById con misma lógica — `ven/service/VentaService.java:30-42` — tres métodos list/listByFechaLocal/getById con misma lógica. Un solo método con Optional<Instant>.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-003` list() con 5 ramas if/else; usar Specification — `com/service/CompraService.java:62-77` — list() con 5 ramas if/else; usar Specification.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-004` guardarRelaciones con sets añadidos/eliminados en for; anidados — `ven/service/PromocionService.java:163-200`
  - **Acción**: Map.putIfAbsent + removeIf _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-005` list con 4 ramas; difícil de cubrir con tests — `ven/service/VentaService.java:71-76` — list con 4 ramas; difícil de cubrir con tests. Refactor a Specification.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-006` toResponse fuerza SELECT extra por categoría lazy — `cat/service/ProductoService.java:107-121` — toResponse fuerza SELECT extra por categoría lazy.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-007` OpenTelemetry SDK + Java agent ambos; SDK manual no usado — `build.gradle.kts:25-32` — OpenTelemetry SDK + Java agent ambos; SDK manual no usado. Eliminar SDK o usar.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-008` magic values "*", "**"; constantes — `common/web/CorsConfigurationFactory.java:32-52` — magic values "*", "**"; constantes.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-009` COALESCE(:estado, c — `ven/repo/CotizacionRepository.java:10-18` — COALESCE(:estado, c.estado) genera WHERE c.estado = c.estado. Preferir Specification.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-010` DataClassRowMapper — `com/service/CompraService.java:23-29` — DataClassRowMapper.newInstance(...) por método. Cachear static final por tipo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM / LOW (resumen)** (1)

- **LOW** `BACK-MAN-011` Sort — `common/web/PageQuery.java:37-39` — Sort.by no valida prop; con JPA propiedad inexistente lanza 500.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

---

### 3.2 ferreteriaFront

**Stack canónico** (de `audits/stacks.yaml`):

- **Runtime**: React 19.2.8
- **Language**: TypeScript 6.0.2
- **Build**: Vite 8.2.2 (rolldown + React Compiler via @rolldown/plugin-babel)
- **Otros**: `ui`=Tailwind 4.3.3, `state`=Zustand 5.0.15, TanStack Query 5.102.8, `routing`=React Router 7.18.2, `charts`=Recharts 3.10.1, `dialogs`=SweetAlert2 11.14.5, `icons`=lucide-react 1.34.0, `http`=axios 1.20.x, `telemetry`=@opentelemetry/* (browser SDK), `i18n`=es-MX, en, `test`=NONE (no test runner installed), `lint`=ESLint 10.9.0, `pkg_manager`=bun (bun.lock present)

#### 3.2.1 Rendimiento

**HIGH** (2)

- **HIGH** `FRONT-REND-001` 6 listeners (mousemove, mousedown, keydown, scroll, touchstart, click) sin throt — `src/hooks/useInactivityTimeout.ts:41-55` — 6 listeners (mousemove, mousedown, keydown, scroll, touchstart, click) sin throttle/debounce. mousemove puede dispararse >100/seg → cada llamada actualiza useAuthStore.lastActivityAt ⇒ re-render de todos los selectores suscritos
  - **Acción**: throttle 1s o timestamp local _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `FRONT-REND-002` 50+ lazy() con <Suspense fallback={spinners — `src/router/router.tsx:104-520` — 50+ lazy() con <Suspense fallback={spinners.full}> ⇒ solo spinner global. Sin esqueletos contextuales por ruta
  - **Acción**: Suspense por feature _(blast_radius: `perf`; confidence: `HIGH`)_

**MEDIUM** (6)

- **MEDIUM** `FRONT-REND-003` retry: 1 en TanStack Query + apiMaxRetries=3 en axios ⇒ 1+3 = 4 intentos para 5x — `src/main.tsx:11-19` — retry: 1 en TanStack Query + apiMaxRetries=3 en axios ⇒ 1+3 = 4 intentos para 5xx
  - **Acción**: documentar o reducir retries _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-REND-004` useQuery por cada cambio en busqueda sin debounce — `src/features/pos/PosPage.tsx:164-174`
  - **Acción**: debounce 200-300ms _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-REND-005` src/features/reportes/* — `useReporte.ts:6-15` — src/features/reportes/*.tsx — 7 páginas con patrón duplicado (useState(rangoFechas()) + useQuery + useEffect(error→toast)). El hook existe pero **no se usa**.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-REND-006` String(err) sobre AxiosError; ruido en logs/toast — `src/features/pos/PosPage.tsx:316` · `client.ts:50`
  - **Acción**: mensajeError(err) ya existe en _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-REND-007` sin manualChunks para recharts, @opentelemetry/*, sweetalert2 — `vite.config.ts:1-38` — sin manualChunks para recharts, @opentelemetry/*, sweetalert2. Bundle splitting ineficiente.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-REND-008` sin chunkSizeWarningLimit, sin target: 'es2020' — `vite.config.ts:1-38` — sin chunkSizeWarningLimit, sin target: 'es2020'.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**LOW** (2)

- **LOW** `FRONT-REND-009` effect auto-add depende de agregar que cambia cada render — `src/features/pos/PosPage.tsx:282` — effect auto-add depende de agregar que cambia cada render. Usar useRef.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `FRONT-REND-010` sin virtualización para tablas grandes — `src/components/ui/DataTable.tsx:24-75` — sin virtualización para tablas grandes.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.2.2 Seguridad

**HIGH** (3)

- **HIGH** `FRONT-SEC-001` clasificación CSRF **demasiado amplia**: cualquier 403 mutating se reintenta com — `src/lib/api/client.ts:173-189` — clasificación CSRF **demasiado amplia**: cualquier 403 mutating se reintenta como CSRF. Un 403 legítimo (permisos) puede enmascararse como éxito
  - **Acción**: diferenciar por error.response.data?.codigo === 'CSRF_TOKEN_INVALID' _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `FRONT-SEC-002` + — flag autenticado en localStorage como única verdad; no hay GET /auth/me en b — `src/store/auth.ts:22-58` · `src/router/guards.tsx:8-15` — + — flag autenticado en localStorage como única verdad; no hay GET /auth/me en boot. UI puede mentir sobre estado de sesión
  - **Acción**: revalidar contra backend en bootstrap _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `FRONT-SEC-003` sin CSP, HSTS, COOP, CORP — `nginx.conf:113-120` — sin CSP, HSTS, COOP, CORP. XSS defense 100% en React
  - **Acción**: CSP estricta _(blast_radius: `security`; confidence: `HIGH`)_

**MEDIUM** (3)

- **MEDIUM** `FRONT-SEC-004` RequiereRol lee rol del store persistido — `src/router/guards.tsx:18-30` — RequiereRol lee rol del store persistido. Si backend invalida el rol, UI sigue con permisos hasta refresh.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-SEC-005` usuario? — `src/components/layout/AppShell.tsx:459-470` — usuario?.empleado?.nombreCompleto?.charAt(0) ?? usuario?.username.charAt(0); el ?. no cubre username
  - **Acción**: chaining opcional completo _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-SEC-006` y reportes — String(err) puede filtrar mensajes crudos al DOM — `src/features/pos/PosPage.tsx:316` — y reportes — String(err) puede filtrar mensajes crudos al DOM.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

**LOW** (1)

- **LOW** `FRONT-SEC-007` this — `src/components/errors/ErrorBoundary.tsx:39` — this.state.error.message renderizado al usuario; puede filtrar paths.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

#### 3.2.3 Diseño

**MEDIUM / LOW (resumen)** (1)

- **MEDIUM** `FRONT-DIS-001` + — copy-paste literal: ambos dicen "Productos sin Movimiento" / "Cuadratura de — `src/features/reportes/MejoresCategoriasPage.tsx:75-79` · `ProductosSinMovimientoPage.tsx:81-83` — + — copy-paste literal: ambos dicen "Productos sin Movimiento" / "Cuadratura de cortes por día" cuando deberían describir el reporte correcto. **Bug literal**.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM / LOW (resumen)** (2)

- **LOW** `FRONT-DIS-002` header con border-b aunque no haya título — `src/components/ui/Card.tsx:21-26` — header con border-b aunque no haya título.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-DIS-003` botón "Cambiar contraseña" hardcodeado en español en lugar de t("appshell — `src/components/layout/AppShell.tsx:486-488` — botón "Cambiar contraseña" hardcodeado en español en lugar de t("appshell.cambiarContrasena").
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.2.4 Estabilidad

**HIGH** (2)

- **HIGH** `FRONT-EST-001` document — `src/components/ui/Dialog.tsx:67-82` — document.body.style.overflow = "hidden" se asigna en cada useEffect; cleanup restaura valor capturado al momento. Varios diálogos solapados → restauración incorrecta
  - **Acción**: contador de referencias activas _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-EST-002` // eslint-disable-next-line react-hooks/exhaustive-deps con deps [autenticado] — `src/hooks/useInactivityTimeout.ts:82` — // eslint-disable-next-line react-hooks/exhaustive-deps con deps [autenticado]. toast y t quedan con referencias viejas tras cambio de idioma
  - **Acción**: useRef o recrear effect _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (3)

- **MEDIUM** `FRONT-EST-003` loading() retorna close que llama Swal — `src/components/ui/Toast.tsx:69-82` — loading() retorna close que llama Swal.close() sin chequear qué modal está abierto. Si dos loading() se solapan, el primer close() cierra el segundo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-EST-004` auto-add barcode depende de useEffect con eslint-disable react-hooks/set-state-i — `src/features/pos/PosPage.tsx:269-283` — auto-add barcode depende de useEffect con eslint-disable react-hooks/set-state-in-effect. Race condition: usuario escanea rápido y el effect lee resultados.data viejo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-EST-005` let refreshing: Promise<string> \| null = null módulo-level; 5+ callers en cola r — `src/lib/api/client.ts:114-128` — let refreshing: Promise<string> \| null = null módulo-level; 5+ callers en cola reciben el mismo error y llaman clearSession() todos
  - **Acción**: distinguir 401/refresh-fallido de 5xx _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (2)

- **LOW** `FRONT-EST-006` puedeRefrescar falla si path incluye query string o encoding — `src/lib/api/client.ts:201-210` — puedeRefrescar falla si path incluye query string o encoding.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-EST-007` listeners de error/unhandledrejection nunca se desregistran; sin e — `src/telemetry/otel.ts:144-155` — listeners de error/unhandledrejection nunca se desregistran; sin e.preventDefault().
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.2.5 Escalabilidad

**MEDIUM / LOW (resumen)** (3)

- **MEDIUM** `FRONT-ESC-001` 50+ lazy() inline en un único createBrowserRouter — `src/router/router.tsx:104-520` — 50+ lazy() inline en un único createBrowserRouter. Cada feature obliga a editar este archivo
  - **Acción**: registry por feature + flatten() _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ESC-002` src/features/reportes/* — `useReporte.ts:6-15` — src/features/reportes/*.tsx — 7 páginas con mismo patrón. El hook existe pero no se usa. Usar o eliminar.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ESC-003` 4 responsabilidades (cliente axios, CSRF, refresh+retry, error mapping) — `src/lib/api/client.ts:1-267` — 4 responsabilidades (cliente axios, CSRF, refresh+retry, error mapping). Separar en módulos.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**MEDIUM / LOW (resumen)** (1)

- **LOW** `FRONT-ESC-004` MOTIVOS_MOVIMIENTO, TIPOS_PRODUCTO, FORMAS_PAGO, PUESTOS, TIPOS_GASTO hardcodead — `src/lib/api/types.ts:230-303` — MOTIVOS_MOVIMIENTO, TIPOS_PRODUCTO, FORMAS_PAGO, PUESTOS, TIPOS_GASTO hardcodeados; sincronización manual con seed SQL. Documentar o exponer endpoint cat_*.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.2.6 Accesibilidad

**HIGH** (1)

- **HIGH** `FRONT-ACC-001` serialización children a string — `src/components/ui/ConfirmDialog.tsx:65-71` — serialización children a string. JSX complejo (<p>¿Seguro? <strong>{name}</strong></p>) se descarta por completo. Contrato frágil.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (5)

- **MEDIUM** `FRONT-ACC-002` focus trap manual con setTimeout(focus, 0) puede correr antes de que los hijos m — `src/components/ui/Dialog.tsx:44-65` — focus trap manual con setTimeout(focus, 0) puede correr antes de que los hijos monten
  - **Acción**: requestAnimationFrame o useLayoutEffect + observer _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ACC-003` sin role="status" ni aria-live — `src/components/ui/EmptyState.tsx:17` — sin role="status" ni aria-live. Screen reader no anuncia empty state
  - **Acción**: role="status" aria-live="polite" _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ACC-004` atajos F1/F2 no documentados (sin tooltip persistente, sin <kbd>, sin comando "M — `src/features/pos/PosPage.tsx:356-373` — atajos F1/F2 no documentados (sin tooltip persistente, sin <kbd>, sin comando "Mostrar atajos")
  - **Acción**: diálogo modal con lista _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ACC-005` botones -/+ con aria-label="Menos"/"Más" genéricos — `src/features/pos/PosPage.tsx:715-718`
  - **Acción**: aria-label={Quitar uno de ${l.nombre}} _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-ACC-006` POS es página accesible-crítica (cajeros con discapacidad, low light) — `src/features/pos/PosPage.tsx:1-1164` — POS es página accesible-crítica (cajeros con discapacidad, low light). Sin aria-keyshortcuts. Asume barcode numérico — incompatible con accesibilidad.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (4)

- **LOW** `FRONT-ACC-007` inputs type="date" sin aria-label propio — `src/components/ui/DateRangePicker.tsx:63-89` — inputs type="date" sin aria-label propio.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-ACC-008` botón tema cambia icono sin anunciar estado (aria-pressed) — `src/components/layout/AppShell.tsx:386-394` — botón tema cambia icono sin anunciar estado (aria-pressed).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-ACC-009` SweetAlert2 provee aria-live; loading() no anuncia "Cargando…" dinámico — `src/components/ui/Toast.tsx:50` — SweetAlert2 provee aria-live; loading() no anuncia "Cargando…" dinámico.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-ACC-010` sin eslint-plugin-jsx-a11y — `eslint.config.js:8-21` — sin eslint-plugin-jsx-a11y.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.2.7 UI/UX

**MEDIUM / LOW (resumen)** (7)

- **MEDIUM** `FRONT-UI-001` modal "Venta registrada" usa new Date( — `src/features/pos/PosPage.tsx:1042-1051` — modal "Venta registrada" usa new Date(...).toLocaleString("es-MX") hardcodeado; el resto usa formatoFechaHora(iso) con i18n.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-002` modal de éxito se cierra a 3s con setTimeout; sin botón "Cerrar" visible — `src/features/pos/PosPage.tsx:313-314` — modal de éxito se cierra a 3s con setTimeout; sin botón "Cerrar" visible.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-003` limpiarTicket() también limpia notas, recibido, referencia — `src/features/pos/PosPage.tsx:308` — limpiarTicket() también limpia notas, recibido, referencia. Si cajero quiere anotar algo para la siguiente venta, se pierde.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-004` almacenId/cajaId en localStorage["ferreteria-pos"] — `src/features/pos/PosPage.tsx:131-135, 197-207` — almacenId/cajaId en localStorage["ferreteria-pos"]. Cambio silencioso entre sesiones.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-005` queryKey sin rango; API acepta rango — `src/features/reportes/MejoresCategoriasPage.tsx:23-25` — queryKey sin rango; API acepta rango. Si backend soporta, incluir; si no, eliminar rango del state.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-006` isRetryable — retries 5xx con backoff sin jitter — `client.ts:139-153` — isRetryable — retries 5xx con backoff sin jitter. Thundering herd.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-UI-007` axios — `client.ts:71-79` — axios.create({ timeout: 30_000 }) alto para POS
  - **Acción**: 8s default, slowAxios(opts) para explícitos _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM / LOW (resumen)** (2)

- **LOW** `FRONT-UI-008` badge "código" no comunica auto-add — `src/features/pos/PosPage.tsx:269-283` — badge "código" no comunica auto-add.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `FRONT-UI-009` topbar móvil sin usuario ni accesos rápidos — `src/components/layout/AppShell.tsx:493-513` — topbar móvil sin usuario ni accesos rápidos.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.2.8 Mantenibilidad

**CRITICAL** (1)

- **CRITICAL** `FRONT-MAN-001` [CR #7] — **sin test script, sin vitest/jest, sin @testing-library** — `package.json:6-11` — [CR #7] — **sin test script, sin vitest/jest, sin @testing-library**. Búsqueda confirma **0 archivos *.test.*/*.spec.*** en src/. Riesgo crítico en POS/finanzas.
  - **Acción**: (definir acción concreta) _(blast_radius: `regression`; confidence: `HIGH`)_

**HIGH** (3)

- **HIGH** `FRONT-MAN-002` 1164 líneas, imposible de probar unitariamente sin mount completo — `src/features/pos/PosPage.tsx:1-1164` — 1164 líneas, imposible de probar unitariamente sin mount completo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-MAN-003` 4 responsabilidades mezcladas — `src/lib/api/client.ts:1-267` — 4 responsabilidades mezcladas. Refactor en client-base.ts, csrf.ts, refresh.ts, errors.ts.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `FRONT-MAN-004` ↔ ↔ — **mismatch de claves i18n**: rango — `src/i18n/es/rango.ts:1-8` · `src/lib/rango.ts:55-61` · `src/components/ui/DateRangePicker.tsx:57` — ↔ ↔ — **mismatch de claves i18n**: rango.ultimos-7 no existe en i18n; botones muestran la clave cruda.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (4)

- **MEDIUM** `FRONT-MAN-005` getTracer() exportado pero no usado (código muerto) — `src/telemetry/otel.ts:175-177` — getTracer() exportado pero no usado (código muerto).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-MAN-006` faltan reglas: @typescript-eslint/no-floating-promises, no-misused-promises, rea — `eslint.config.js:8-21` — faltan reglas: @typescript-eslint/no-floating-promises, no-misused-promises, react-compiler/react-compiler.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-MAN-007` "erasableSyntaxOnly": true impide enum, namespace, parameter properties — `tsconfig.app.json:23` — "erasableSyntaxOnly": true impide enum, namespace, parameter properties. Documentar.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `FRONT-MAN-008` ruido visual; mover o eliminar — `src/features/reportes/CardListReportes.tsx:1-21` — ruido visual; mover o eliminar.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (1)

- **LOW** `FRONT-MAN-009` ayer calculado en módulo; si vive >24h, stale — `src/components/ui/DateRangePicker.tsx:18-19, 24-36` — ayer calculado en módulo; si vive >24h, stale.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

---

### 3.3 ferreteriaDB

**Stack canónico** (de `audits/stacks.yaml`):

- **Runtime**: PostgreSQL 14+ (target) / 17 (compose)
- **Otros**: `schema_count`=9 (cat, cfg, rh, seg, inv, com, ven, fin, fis), `migrations`=Flyway (V1__base, V2__parametria, plus deltas), `pool`=PgBouncer (transaction mode), `observability`=postgres-exporter, OpenTelemetry Collector, Prometheus, Grafana, Tempo, `deploy_targets`=['Podman Compose', 'Kubernetes', 'Terraform']

#### 3.3.1 Rendimiento

**HIGH** (7)

- **HIGH** `DB-REND-001` fin — `scripts/02_tablas.sql:1725-1739` — fin.fn_cerrar_turno ejecuta 4 jsonb_object_agg sobre TODO el set de movimientos del turno en cada cierre
  - **Acción**: materializar resumen en fin.movimientos_caja + índice (turno_caja_id, concepto, tipo) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-002` com — `scripts/02_tablas.sql:1481-1491` — com.fn_detalle_compra_entrada recalcula stock previo con SUM sobre TODO inv.movimientos_inventario filtrado por producto en cada línea → O(n²) en compras grandes
  - **Acción**: leer stock de inv.inventario directamente + WPCC incremental _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-003` ven — `scripts/02_tablas.sql:1330-1420` — ven.fn_recalc_totales_venta se dispara AFTER INSERT OR DELETE por fila → N recálculos por venta de N líneas
  - **Acción**: cambiar a AFTER … FOR EACH STATEMENT con pg_trigger_depth() _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-004` inv — `scripts/02_tablas.sql:300-330` — inv.inventario PK (producto_id, almacen_id) con stock actualizado por trigger AFTER INSERT ⇒ contención en SKUs alta rotación.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-REND-005` seg — `scripts/02_tablas.sql:206-220` — seg.auditoria sin partitioning/retention. ~315 M filas/año.
  - **Acción**: (definir acción concreta) _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **HIGH** `DB-REND-006` inv — `scripts/02_tablas.sql:314-331` — inv.movimientos_inventario (BIGINT IDENTITY, append-only, sin partition). 100-500 k filas/mes
  - **Acción**: RANGE partition por creado_en mensual _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **HIGH** `DB-REND-007` ven — `scripts/02_tablas.sql:634-697, 875-912, 943-983` — ven.ventas, fin.movimientos_caja, fin.cortes_caja sin partitioning.
  - **Acción**: (definir acción concreta) _(blast_radius: `data_loss`; confidence: `HIGH`)_

**MEDIUM** (13)

- **MEDIUM** `DB-REND-008` falta índice (almacen_id, motivo_id, creado_en DESC) para fn_cerrar_turno — `scripts/02_tablas.sql:329-331` — falta índice (almacen_id, motivo_id, creado_en DESC) para fn_cerrar_turno.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-009` idx_nominas_estado_fecha(estado, periodo_fin) no incluye empleado_id — `scripts/02_tablas.sql:242` — idx_nominas_estado_fecha(estado, periodo_fin) no incluye empleado_id.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-010` pérdidas inventario usa m — `scripts/02_tablas.sql:1742-1750` — pérdidas inventario usa m.creado_en BETWEEN v_apertura_en AND now() con m.creado_en no indexado por rango.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-011` vw_ventas_totales, vw_mejores_vendedores, vw_mejores_dias_venta con SUM(total) s — `scripts/vistas_core.sql:75-95, 218-240, 243-260` — vw_ventas_totales, vw_mejores_vendedores, vw_mejores_dias_venta con SUM(total) sobre toda la historia.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-012` ven — `scripts/vistas_core.sql:382-409` — ven.vw_resumen_dashboard ejecuta 10 subqueries en cada refresh.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-013` inv — `scripts/02_tablas.sql:1218-1242` — inv.fn_aplica_movimiento_stock hace 2 lecturas; añadir FOR UPDATE.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-014` ven — `scripts/02_tablas.sql:1257-1278` — ven.fn_detalle_valida_stock valida antes del INSERT; ventana para overselling
  - **Acción**: SELECT … FOR UPDATE o trigger BEFORE INSERT que reserve _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-015` ven — `scripts/02_tablas.sql:1793` — ven.fn_promo_para_producto marcada STABLE pero usa CURRENT_TIMESTAMP
  - **Acción**: VOLATILE o pasar fecha como parámetro _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-016` cfg — `scripts/02_tablas.sql:1066-1081` — cfg.fn_siguiente_folio sin FOR UPDATE; 2 INSERTs concurrentes pueden leer mismo consecivo.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-017` chk_nxm_coherente no exige lleva > 0 — `scripts/02_tablas.sql:1791-1835`
  - **Acción**: añadir al CHECK _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-018` com — `scripts/02_tablas.sql:1508` — com.fn_recalc_totales_compra setea subtotal = v_tot (no separa IVA).
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-019` autovacuum_vacuum_scale_factor=0 — `deploy/conf/postgresql.conf:51-56` — autovacuum_vacuum_scale_factor=0.05 global
  - **Acción**: ALTER TABLE … SET (autovacuum_vacuum_scale_factor = 0.02) por tabla caliente _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-REND-020` trigger trg_det_venta_totales re-ajusta pagos CONTADO con FOR UPDATE; DELETE de — `scripts/02_tablas.sql:1400-1414` — trigger trg_det_venta_totales re-ajusta pagos CONTADO con FOR UPDATE; DELETE de pagos no dispara trg_pago_cliente_post. Lógica inline duplicada.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**LOW** (5)

- **LOW** `DB-REND-021` seg — `scripts/02_tablas.sql:209-220` — seg.auditoria.registro_id = 0 cuando PK no existe en la fila.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-REND-022` v_uid INTEGER := NULLIF(current_setting('app — `scripts/02_tablas.sql:1118` — v_uid INTEGER := NULLIF(current_setting('app.usuario_id', true), '')::INTEGER se lee una vez por invocación de trigger, no por fila.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-REND-023` inv — `scripts/vistas_core.sql:46-72` — inv.vw_stock_bajo con ORDER BY alerta DESC, i.stock ASC sin índice.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-REND-024` inv — `scripts/vistas_core.sql:307-336` — inv.vw_productos_sin_movimiento recalcula última venta de todos los productos en cada refresh.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-REND-025` pgbouncer/pgbouncer:latest sin tag inmutable — `deploy/docker-compose.yml:392` — pgbouncer/pgbouncer:latest sin tag inmutable.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.3.2 Seguridad

**CRITICAL** (1)

- **CRITICAL** `DB-SEC-001` CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION' — `scripts/01_base_esquemas.sql:29` — CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION'. Si se ejecuta tal cual, login con password trivial
  - **Acción**: ALTER ROLE … WITH PASSWORD NULL _(blast_radius: `security`; confidence: `HIGH`)_

**HIGH** (6)

- **HIGH** `DB-SEC-002` local all all trust permite acceso sin auth dentro del contenedor — `deploy/conf/pg_hba.conf:2`
  - **Acción**: peer o scram-sha-256 _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-003` GRANT SELECT, INSERT, UPDATE, DELETE total a ferreteria_app sobre TODAS las tabl — `scripts/02_tablas.sql:1905-1919` — GRANT SELECT, INSERT, UPDATE, DELETE total a ferreteria_app sobre TODAS las tablas (incl. seg.usuarios, cfg.configuracion, fis.facturas)
  - **Acción**: segregar roles (ferreteria_ro, ferreteria_pos, ferreteria_admin) con GRANTs específicos _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-004` solo inv — `scripts/02_tablas.sql:1918-1919` — solo inv.movimientos_inventario, fin.movimientos_caja, seg.auditoria tienen REVOKE DELETE. ven.ventas, ven.cuentas_cobrar, com.compras, com.cuentas_pagar, fin.cortes_caja quedan con DELETE habilitado
  - **Acción**: REVOKE DELETE en todas las ledger; cancelaciones vía UPDATE de estado _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-005` ninguna función tiene SET search_path ni SECURITY DEFINER → **search_path hijack — `scripts/02_tablas.sql:1118, 1218-1242, 1330-1420, 1678-1780` — ninguna función tiene SET search_path ni SECURITY DEFINER → **search_path hijack** risk
  - **Acción**: añadir SET search_path = pg_catalog, public, … en cada CREATE OR REPLACE FUNCTION _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-006` seg — `scripts/02_tablas.sql:208-220` — seg.auditoria almacena password_hash (bcrypt) en datos_anteriores/datos_nuevos (via to_jsonb(NEW)). Cualquier SELECT expone el hash
  - **Acción**: WHEN clause que excluya password_hash, o enmascarar _(blast_radius: `security`; confidence: `HIGH`)_

- **HIGH** `DB-SEC-007` host … 0 — `deploy/conf/pg_hba.conf:4-5` — host … 0.0.0.0/0 … scram-sha-256 y ::/0 aceptan la app desde cualquier IP
  - **Acción**: restringir a 10.0.0.0/8 _(blast_radius: `security`; confidence: `HIGH`)_

**MEDIUM** (10)

- **MEDIUM** `DB-SEC-008` seg — `scripts/02_tablas.sql:978, 1917` — seg.auditoria UPDATE no rompe nada, DELETE sí. Trigger BEFORE DELETE RAISE EXCEPTION análogo a kardex.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-009` userlist — `deploy/docker-compose.yml:432-433` — userlist.txt con printf vulnerable a caracteres especiales.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-010` seg — `scripts/02_tablas.sql:174-183` — seg.usuarios sin failed_login_attempts, locked_until, must_change_password, password_changed_at
  - **Acción**: columnas + trigger o delegar a backend _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-011` seg — `scripts/02_tablas.sql:182` — seg.usuarios.eliminado_en sin trigger que valide activo=false ⇒ eliminado_en IS NOT NULL.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-012` password_hash VARCHAR(255) con bcrypt(12) ~60 chars — `scripts/02_tablas.sql:178` — password_hash VARCHAR(255) con bcrypt(12) ~60 chars. Considerar TEXT.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-013` PG_PASSWORD y JWT_SECRET via env vars legibles con docker inspect — `deploy/docker-compose.yml:84, 89`
  - **Acción**: secrets: (Compose) o K8s Secret _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-014` host all postgres 127 — `deploy/conf/pg_hba.conf:10-11` — host all postgres 127.0.0.1/32 trust permite privilege escalation si el contenedor es comprometido
  - **Acción**: peer _(blast_radius: `security`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-015` UPDATE inline a cat — `scripts/03_parametria.sql:394-402` — UPDATE inline a cat.formas_pago con fis.formas_pago_sat. Si SAT cambia clave, JOINs rotos
  - **Acción**: sat.clave_hist inmutable + sat.clave actualizable _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-016` cfg — `scripts/02_tablas.sql:1066-1081` — cfg.fn_siguiente_folio con placeholder '' puede generar 00000001 sin prefijo.
  - **Acción**: (definir acción concreta) _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

- **MEDIUM** `DB-SEC-017` CHECK (rfc ~* — `scripts/02_tablas.sql:99` — CHECK (rfc ~* ...) valida formato pero no contra SAT/blacklist.
  - **Acción**: (definir acción concreta) _(blast_radius: `financial_fraud`; confidence: `HIGH`)_

**LOW** (3)

- **LOW** `DB-SEC-018` postgres-exporter se conecta como postgres (superuser) — `deploy/docker-compose.yml:191-194`
  - **Acción**: rol dedicado ferreteria_metrics _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `DB-SEC-019` cat — `scripts/03_parametria.sql:154-157` — cat.categorias.ruta no incluye abuelo para nivel >=3
  - **Acción**: CTE recursivo o trigger _(blast_radius: `security`; confidence: `HIGH`)_

- **LOW** `DB-SEC-020` seg — `scripts/02_tablas.sql:206` — seg.sesiones.ip_address INET sin validación de rango.
  - **Acción**: (definir acción concreta) _(blast_radius: `security`; confidence: `HIGH`)_

#### 3.3.3 Diseño

**HIGH** (8)

- **HIGH** `DB-DIS-001` convención de PK inconsistente (BIGINT vs INTEGER) — `scripts/02_tablas.sql:485, 591, 635, 876, 915`
  - **Acción**: BIGINT para transaccional, INTEGER solo catálogos con cardinalidad acotada _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-002` cat — `scripts/02_tablas.sql:34, 78, 80` — cat.categorias.nombre VARCHAR(100) NOT NULL sin UNIQUE global
  - **Acción**: UNIQUE (padre_id, nombre) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-003` ven — `scripts/02_tablas.sql:621` — ven.cotizaciones.venta_generada_id BIGINT sin FK declarada en CREATE TABLE (FK se añade después)
  - **Acción**: FK inline o DEFERRABLE INITIALLY DEFERRED _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-004` columnas GENERATED ALWAYS AS … STORED no documentadas como "no incluir en INSERT — `scripts/02_tablas.sql:464, 670, 813, 959-961` — columnas GENERATED ALWAYS AS … STORED no documentadas como "no incluir en INSERT"
  - **Acción**: documentar o BEFORE INSERT triggers _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-005` funciones de totales mezclan cálculo con transición de estado — `scripts/02_tablas.sql:1354-1356, 1508, 1754-1761, 1430-1434, 1595-1599`
  - **Acción**: separar en funciones puras testeables (fn_calcular_estado_cuenta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-006` fecha_local DATE GENERATED … depende del GUC timezone; PgBouncer transaction mod — `scripts/02_tablas.sql:642, 705, 793, 612` — fecha_local DATE GENERATED … depende del GUC timezone; PgBouncer transaction mode puede no propagarlo
  - **Acción**: ALTER ROLE … SET timezone=… (ya existe L36) y/o cast explícito _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-007` funciones API en plpgsql cuando son esencialmente INSERT/UPDATE — `scripts/02_tablas.sql:1066-1081, 1840-1866, 1873-1895`
  - **Acción**: LANGUAGE sql para inline en el planner _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-DIS-008` soft-delete model mixto — `scripts/02_tablas.sql:206-220, 314-331, 875-912, 943-983`
  - **Acción**: unificar con eliminado_en TIMESTAMPTZ + vista vw_<tabla>_activas _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (14)

- **MEDIUM** `DB-DIS-009` regex RFC duplicada literal — `scripts/02_tablas.sql:97, 490`
  - **Acción**: CREATE DOMAIN d_rfc _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-010` inv — `scripts/02_tablas.sql:296` — inv.almacenes.es_punto_venta permite múltiples PV; FK fin.cajas.almacen_id no garantiza PV.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-011` uq_linea_activa_por_cliente parcial; no impide N suspendidas — `scripts/02_tablas.sql:574, 585, 603` — uq_linea_activa_por_cliente parcial; no impide N suspendidas.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-012` cfg — `scripts/02_tablas.sql:117-122` — cfg.configuracion key/value sin validación de tipo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-013` ven — `scripts/02_tablas.sql:1793` — ven.fn_promo_para_producto no aplica max_usos_total.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-014` trigger admite ejecución múltiple — `scripts/02_tablas.sql:1383-1385`
  - **Acción**: STATEMENT-level + pg_trigger_depth() _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-015` múltiples turno_caja_id BIGINT sin FK inline — `scripts/02_tablas.sql:359, 678, 711, 766` — múltiples turno_caja_id BIGINT sin FK inline.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-016` idx_productos_activos(categoria_id) WHERE activo redundante con idx_productos_ca — `scripts/02_tablas.sql:269-273` — idx_productos_activos(categoria_id) WHERE activo redundante con idx_productos_categoria.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-017` faltan índices fecha en rh — `scripts/02_tablas.sql:509-510, 622-623, 728, 805, 928, 940` — faltan índices fecha en rh.nominas, fin.cortes_caja, seg.auditoria.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-018` fis — `scripts/02_tablas.sql:434` — fis.claves_prod_serv.clave PK sin índice de búsqueda parcial.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-019` fin — `scripts/02_tablas.sql:976-978` — fin.cortes_caja "inmutable" sin trigger ni REVOKE.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-020` sección "M — `scripts/02_tablas.sql:1062-1063` — sección "M. FUNCIONES Y TRIGGERS" mezcla funciones, triggers, GRANTs
  - **Acción**: separar archivos _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-021` seed ciudades solo 12 — `scripts/03_parametria.sql:226-236` — seed ciudades solo 12. Incompleto para producción (México tiene +2400 INEGI).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-DIS-022` usuario_registra_id, usuario_id sin ON DELETE — `scripts/02_tablas.sql:226-241, 875-887` — usuario_registra_id, usuario_id sin ON DELETE.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.3.4 Estabilidad

**HIGH** (10)

- **HIGH** `DB-EST-001` trg_mov_stock (AFTER INSERT) puede dejar stock desincronizado con rollback parci — `scripts/02_tablas.sql:1218-1246, 1248-1254` — trg_mov_stock (AFTER INSERT) puede dejar stock desincronizado con rollback parcial
  - **Acción**: BEFORE INSERT + UPDATE atómico _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-002` ven — `scripts/02_tablas.sql:1322-1326` — ven.fn_valida_credito sin FOR UPDATE sobre ven.cuentas_cobrar → race condition "credit check + write"
  - **Acción**: LOCK TABLE o SELECT … FOR UPDATE _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-003` fn_recalc_totales_venta/compra AFTER-per-row disparan N veces por venta → riesgo — `scripts/02_tablas.sql:1330-1420, 1499-1585` — fn_recalc_totales_venta/compra AFTER-per-row disparan N veces por venta → riesgo de deadlock entre ven.ventas, ven.cuentas_cobrar, ven.pagos_cliente
  - **Acción**: trigger STATEMENT-level + pg_trigger_depth()=0 _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-004` FOR UPDATE sin ORDER BY consistente → deadlocks intermitentes — `scripts/02_tablas.sql:1364, 1403, 1516, 1558, 1696, 1845`
  - **Acción**: documentar convención de lock acquisition; considerar SKIP LOCKED _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-005` fin — `scripts/02_tablas.sql:1718-1739` — fin.fn_cerrar_turno toma FOR UPDATE sobre fin.turnos_caja pero NO sobre fin.movimientos_caja → movimientos fantasma
  - **Acción**: LOCK TABLE fin.movimientos_caja IN SHARE MODE _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-006` cfg — `scripts/02_tablas.sql:1066-1081` — cfg.fn_siguiente_folio sin FOR UPDATE → dos INSERTs concurrentes leen mismo consecivo
  - **Acción**: UPDATE … SET consecutivo = consecutivo + 1 … RETURNING _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-007` deploy/conf/pgbouncer — PgBouncer pool_mode=transaction + DISCARD ALL; current_s — `deploy/docker-compose.yml:431-434,` — deploy/conf/pgbouncer — PgBouncer pool_mode=transaction + DISCARD ALL; current_setting('app.usuario_id', true) depende de SET LOCAL
  - **Acción**: documentar que backend DEBE usar SET LOCAL app.usuario_id = $1 _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-008` triggers AFTER INSERT/UPDATE/DELETE → seg — `scripts/02_tablas.sql:1135, 1138, 1141, 1144, 1147, 1150, 1153` — triggers AFTER INSERT/UPDATE/DELETE → seg.fn_auditar → INSERT en seg.auditoria; si tablespace lleno, cascada de errores
  - **Acción**: monitor + alerta + partition + archivado _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-009` fn_cerrar_turno filtra pérdidas por m — `scripts/02_tablas.sql:1742-1750` — fn_cerrar_turno filtra pérdidas por m.creado_en BETWEEN v_apertura_en AND now(); turnos que abren 00:01 pierden movimientos del día anterior por desfase de zona
  - **Acción**: usar fecha_local o ±1h tolerancia _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-EST-010` trg_kardex_no_upd BEFORE UPDATE OR DELETE no impide TRUNCATE — `scripts/02_tablas.sql:1252-1254`
  - **Acción**: REVOKE TRUNCATE + trigger BEFORE TRUNCATE _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (10)

- **MEDIUM** `DB-EST-011` trigger de totales borra pagos CONTADO; si EFECTIVO ya asentado en fin — `scripts/02_tablas.sql:1394-1415` — trigger de totales borra pagos CONTADO; si EFECTIVO ya asentado en fin.movimientos_caja, movimiento huérfano.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-012` com — `scripts/02_tablas.sql:1481-1491` — com.fn_detalle_compra_entrada O(n²) en producto con muchos movimientos.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-013` ven — `scripts/02_tablas.sql:1302-1328` — ven.fn_valida_credito sin FOR UPDATE sobre ven.lineas_credito.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-014` trg_kardex_no_upd lanza P0999 sin loguear quién lo intentó — `scripts/02_tablas.sql:1248-1254` — trg_kardex_no_upd lanza P0999 sin loguear quién lo intentó.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-015` synchronous_commit = on con max_wal_senders = 5 y streaming — `deploy/conf/postgresql.conf:39-49`
  - **Acción**: si réplica local, synchronous_commit = remote_write reduce latencia 2-3x _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-016` log_min_duration_statement = 500 no captura INSERT/UPDATE masivos — `deploy/conf/postgresql.conf:62` — log_min_duration_statement = 500 no captura INSERT/UPDATE masivos.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-017` idle_in_transaction_session_timeout = 10min agresivo para reportes largos — `deploy/conf/postgresql.conf:77-78` — idle_in_transaction_session_timeout = 10min agresivo para reportes largos.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-018` FOR UPDATE en triggers AFTER puede bloquear INSERT padre — `scripts/02_tablas.sql:1364, 1403, 1558`
  - **Acción**: BEFORE INSERT para validación, AFTER para pagos _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-019` fn_renta_post no valida forma_pago_id si deposito > 0 — `scripts/02_tablas.sql:1650-1671` — fn_renta_post no valida forma_pago_id si deposito > 0.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-EST-020` fn_recalc_totales_venta mezcla v_tot (con IVA) y v_sub (sin IVA) — `scripts/02_tablas.sql:1354-1356` — fn_recalc_totales_venta mezcla v_tot (con IVA) y v_sub (sin IVA).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (4)

- **LOW** `DB-EST-021` DROP TRIGGER IF EXISTS + CREATE TRIGGER verboso (~30 bloques) — `scripts/02_tablas.sql:1115-1156` — DROP TRIGGER IF EXISTS + CREATE TRIGGER verboso (~30 bloques). Helper procedure.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-EST-022` cfg — `scripts/02_tablas.sql:1066` — cfg.fn_siguiente_folio no valida que p_tipo exista.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-EST-023` hard-coded mo — `scripts/02_tablas.sql:1748-1749` — hard-coded mo.clave IN ('DETERIORO','USO_INTERNO','MUESTRA').
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-EST-024` bootstrap replica sin validación post-pg_basebackup — `deploy/docker-compose.yml:367-368` — bootstrap replica sin validación post-pg_basebackup.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.3.5 Escalabilidad

**CRITICAL** (2)

- **CRITICAL** `DB-ESC-001` **ninguna tabla ledger particionada** — `scripts/02_tablas.sql:314-331, 634-697, 875-912, 943-983` — **ninguna tabla ledger particionada**. Tienda mediana 5 años = 900 k ventas + 4.5-9 M venta_detalles
  - **Acción**: PARTITION BY RANGE mensual con pg_partman _(blast_radius: `perf`; confidence: `HIGH`)_

- **CRITICAL** `DB-ESC-002` seg — `scripts/02_tablas.sql:208-220` — seg.auditoria BIGINT, sin partition; **~315 M filas/año ≈ 1 TB/año**
  - **Acción**: PARTITION + TOAST compression + archivado _(blast_radius: `data_loss`; confidence: `HIGH`)_

**HIGH** (8)

- **HIGH** `DB-ESC-003` triggers AFTER INSERT per-row sobre tablas calientes — `scripts/02_tablas.sql:1218-1242, 1330-1420, 1678-1780` — triggers AFTER INSERT per-row sobre tablas calientes. Bulk insert 100× más lento que bulk copy
  - **Acción**: documentar bypass con COPY … WITH (FREEZE) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-004` PgBouncer pool_mode=transaction sin read/write split — `deploy/docker-compose.yml:393-446`
  - **Acción**: PgBouncer secundario session mode para reports o routing por user _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-005` vistas analíticas con GROUP BY date_trunc( — `scripts/vistas_core.sql:9-23, 218-240, 243-260, 262-272` — vistas analíticas con GROUP BY date_trunc(...), RANK() OVER (…)
  - **Acción**: MATERIALIZED VIEW CONCURRENTLY (PG 14+) + índice único _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-006` sequences globales (inv — `scripts/02_tablas.sql:248, 635, 731` — sequences globales (inv.productos, ven.ventas, ven.cuentas_cobrar) son predecibles y WAL hotspot
  - **Acción**: UUIDv7 (pg_uuidv7) para ven.ventas _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-007` inv — `scripts/02_tablas.sql:265, 268` — inv.productos.atributos JSONB sin compresión TOAST; 10 KB × 10 k productos = 100 MB
  - **Acción**: CHECK (length(atributos::text) < 4096) o EAV _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-008` volumen pg_data_primary sin pgBackRest/WAL-G para PITR — `deploy/docker-compose.yml:325-336`
  - **Acción**: pgBackRest stanza=ferreteria repo1-path=/backups (7d/4w/12m) _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-009` PVC sin StorageClass específico — `deploy/docker-compose.yml:309-336,` · `deploy/k8s/10-postgres.yaml:113-120`
  - **Acción**: io2 para OLTP _(blast_radius: `perf`; confidence: `HIGH`)_

- **HIGH** `DB-ESC-010` pg_stat_statements — `deploy/conf/postgresql.conf:66-70` — pg_stat_statements.max = 10000 se rota rápido
  - **Acción**: subir a 50000 _(blast_radius: `perf`; confidence: `HIGH`)_

**MEDIUM** (9)

- **MEDIUM** `DB-ESC-011` imagen_url TEXT confirmar URL externa (no base64) — `scripts/02_tablas.sql:265` — imagen_url TEXT confirmar URL externa (no base64).
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-012` fn_promo_para_producto filtra por CURRENT_TIMESTAMP por fila — `scripts/02_tablas.sql:1793, 1822-1824` — fn_promo_para_producto filtra por CURRENT_TIMESTAMP por fila.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-013` vw_resumen_dashboard 10 subqueries — `scripts/vistas_core.sql:382-409`
  - **Acción**: cfg.dashboard_cache _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-014` inv — `scripts/vistas_core.sql:196-215` — inv.vw_kardex_producto con SUM(...) OVER (PARTITION BY ...) costoso
  - **Acción**: materializar o filtrar por rango _(blast_radius: `data_loss`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-015` fn_cerrar_turno no serializa contra inserts concurrentes — `scripts/02_tablas.sql:1683-1780`
  - **Acción**: LOCK TABLE fin.movimientos_caja IN SHARE MODE _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-016` fin — `scripts/02_tablas.sql:1172-1196` — fin.fn_movimiento_caja SELECT sobre fin.turnos_caja por cada movimiento.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-017` 4 índices trigram — `scripts/02_tablas.sql:269-273, 509-510` — 4 índices trigram. Consolidar.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-018` effective_cache_size=1536MB con 2 GB RAM — `deploy/conf/postgresql.conf:21` — effective_cache_size=1536MB con 2 GB RAM. Parametrizar.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-ESC-019` pool_mode=transaction + DISCARD ALL + current_setting('app — `deploy/docker-compose.yml:431` — pool_mode=transaction + DISCARD ALL + current_setting('app.usuario_id'). Backend debe usar SET LOCAL.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

**LOW** (3)

- **LOW** `DB-ESC-020` inv — `scripts/02_tablas.sql:336` — inv.conteos_fisicos.fecha sin índice.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-ESC-021` idx_producto_impuesto_default parcial; documentar — `scripts/02_tablas.sql:443-444` — idx_producto_impuesto_default parcial; documentar.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **LOW** `DB-ESC-022` log_min_duration_statement = 500 — `deploy/conf/postgresql.conf:62` — log_min_duration_statement = 500. Logear a 100 ms en producción.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

#### 3.3.7 UI/UX

**MEDIUM / LOW (resumen)** (4)

- **MEDIUM** `DB-UI-001` cat — `scripts/02_tablas.sql:33-41` — cat.categorias.ruta TEXT con 'Padre > Hijo'
  - **Acción**: usar LTREE (/Herramientas/Manuales/Martillos) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-UI-002` seg — `scripts/02_tablas.sql:189-194` — seg.rol_permisos/seg.usuario_roles sin asignado_en/asignado_por
  - **Acción**: añadir columnas _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-UI-003` ven — `scripts/02_tablas.sql:534-535, 542-549` — ven.promociones.dias_semana SMALLINT[] codificado 1-7
  - **Acción**: tabla cat.dias_semana _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-UI-004` formas de pago con clave snake_case; UI debe mostrar 'Tarjeta de débito' — `scripts/03_parametria.sql:166-169`
  - **Acción**: columna etiqueta_ui o nombre legible _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM / LOW (resumen)** (4)

- **LOW** `DB-UI-005` seg — `scripts/02_tablas.sql:166-171` — seg.permisos.clave VARCHAR(40) con formato <MODULO>.<ACCION>
  - **Acción**: columna modulo VARCHAR(10) separada _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-UI-006` vw_mejores_dias_venta decodifica EXTRACT(ISODOW) con CASE — `scripts/vistas_core.sql:243-260`
  - **Acción**: cat.dias_semana JOIN _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-UI-007` notas/observaciones libres sin formato — `scripts/02_tablas.sql:328, 467, 671, 758, 916` — notas/observaciones libres sin formato.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **LOW** `DB-UI-008` telefono VARCHAR(20) sin formato — `scripts/02_tablas.sql:493, 745-749` — telefono VARCHAR(20) sin formato.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

#### 3.3.8 Mantenibilidad

**HIGH** (7)

- **HIGH** `DB-MAN-001` **1921 líneas** en un solo archivo con secciones A-Ñ — `scripts/02_tablas.sql:1-1921`
  - **Acción**: dividir (02_tablas_cat.sql, 02_tablas_ven.sql, 02_funciones.sql, 02_triggers.sql, 02_grants.sql) + \ir _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-002` cada trigger con DROP TRIGGER IF EXISTS …; CREATE TRIGGER …; (~30 bloques) — `scripts/02_tablas.sql:1115-1156, 1244-1299, 1422-1466, 1495-1611, 1643-1675`
  - **Acción**: helper procedure _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-003` 25+ funciones sin namespace — `scripts/02_tablas.sql:1066, 1083, 1116, 1157, 1172, 1199, 1218, 1248, 1257, 1284, 1302, 1330, 1427, 1449, 1469, 1499, 1592, 1614, 1633, 1647, 1663, 1678, 1787, 1837, 1869` — 25+ funciones sin namespace. Documentar convención.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-004` funciones completas (re-implementación) en lugar de patch — `migrations/delta_errcodes_negocio.sql:10-288`
  - **Acción**: Flyway R__ repeatable _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-005` DATA_SOURCE_NAME con POSTGRES_ADMIN_PASSWORD interpolado — `deploy/docker-compose.yml:191-194`
  - **Acción**: secret refs _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-006` Job ejecuta con psql … -v ON_ERROR_STOP=1; orden incorrecto falla sin claridad — `deploy/k8s/40-migration-job.yaml:30-49`
  - **Acción**: logging + verificación _(blast_radius: `dx`; confidence: `HIGH`)_

- **HIGH** `DB-MAN-007` DO $$ para FKs con IF NOT EXISTS sobre pg_constraint — `scripts/02_tablas.sql:1049-1059`
  - **Acción**: ALTER TABLE … ADD CONSTRAINT IF NOT EXISTS (PG 16+) _(blast_radius: `dx`; confidence: `HIGH`)_

**MEDIUM** (7)

- **MEDIUM** `DB-MAN-008` DO $$ con lógica que podría ser PL/pgSQL nativo — `scripts/02_tablas.sql:1, 1050` — DO $$ con lógica que podría ser PL/pgSQL nativo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-009` seg — `scripts/02_tablas.sql:159` — seg.roles.clave VARCHAR(30) NOT NULL UNIQUE sin doc de case-sensitivity.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-010` seg — `scripts/02_tablas.sql:166-171` — seg.permisos.clave sin CHECK.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-011` idx_productos_nombre_trgm y idx_productos_activos — uno parcial, otro no — `scripts/02_tablas.sql:269, 273` — idx_productos_nombre_trgm y idx_productos_activos — uno parcial, otro no.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-012` fis — `scripts/03_parametria.sql:391-395` — fis.claves_prod_serv con 3 ejemplos; doc cargar SAT completo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-013` Trigger trg_audit_usuario audita seg — `scripts/02_tablas.sql:1138-1142, 1143-1145` — Trigger trg_audit_usuario audita seg.usuarios (incl. password_hash).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `DB-MAN-014` Índice GIN sobre dias_semana SMALLINT[] (7 valores) excesivo — `scripts/02_tablas.sql:563` — Índice GIN sobre dias_semana SMALLINT[] (7 valores) excesivo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

**LOW** (1)

- **LOW** `DB-MAN-015` hard-coded motivos en fn_cerrar_turno — `scripts/02_tablas.sql:1748-1749` — hard-coded motivos en fn_cerrar_turno.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

---

## §4 Backlog priorizado (top 30)

Auto-derivado de `findings.yaml`. Orden: severidad desc, blast_radius, proyecto, dimensión.

| # | ID | Sev | Componente | Dimensión | Blast | Esfuerzo | Hallazgo | Acción |
|---:|---|:--:|---|---|---|---|---|---|
| 1 | `DB-ESC-002` | CRITICAL | `database` | escalabilidad | `data_loss` | M | seg | PARTITION + TOAST compression + archivado |
| 2 | `BACK-SEC-001` | CRITICAL | `backend` | seguridad | `financial_fraud` | M | cat/api/ConfiguracionController | restringir a ADMINISTRADOR |
| 3 | `BACK-SEC-002` | CRITICAL | `backend` | seguridad | `security` | M | JWT_SECRET con default sMuJeQ1prwGDL5SCu7tpS4FtCcIvbJ8Rw6cciudScgc= | quitar default; @PostConstruct fail-fast |
| 4 | `DB-SEC-001` | CRITICAL | `database` | seguridad | `security` | M | CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION' | ALTER ROLE … WITH PASSWORD NULL |
| 5 | `BACK-REND-001` | CRITICAL | `backend` | rendimiento | `perf` | M | toResponse ejecuta clienteRepo | findAllById(...) batch + Map<Long,T> en mapper |
| 6 | `BACK-REND-002` | CRITICAL | `backend` | rendimiento | `perf` | M | mismo patrón: 3 lookups de catálogo + N lookups de producto por cada detalle | (definir acción concreta) |
| 7 | `BACK-REND-003` | CRITICAL | `backend` | rendimiento | `perf` | M | mismo patrón | (definir acción concreta) |
| 8 | `BACK-REND-004` | CRITICAL | `backend` | rendimiento | `perf` | M | toCuentaResponse 3 búsquedas por cuenta (clienteRepo, ventaRepo, pagoRepo); N+1 | (definir acción concreta) |
| 9 | `BACK-REND-005` | CRITICAL | `backend` | rendimiento | `perf` | M | mismo patrón | (definir acción concreta) |
| 10 | `BACK-REND-006` | CRITICAL | `backend` | rendimiento | `perf` | M | mismo patrón | (definir acción concreta) |
| 11 | `BACK-REND-007` | CRITICAL | `backend` | rendimiento | `perf` | M | listCajas/listTurnos/toCorteResponse iteran almacenRepo | (definir acción concreta) |
| 12 | `BACK-REND-008` | CRITICAL | `backend` | rendimiento | `perf` | M | listUsuarios/listRoles/listPermisos reconstruyen Page manualmente con gateway | (definir acción concreta) |
| 13 | `BACK-REND-009` | CRITICAL | `backend` | rendimiento | `perf` | M | paginación manual con gateway | (definir acción concreta) |
| 14 | `BACK-REND-010` | CRITICAL | `backend` | rendimiento | `perf` | M | generarQuincena y pagarLote hacen jdbc | (definir acción concreta) |
| 15 | `BACK-REND-011` | CRITICAL | `backend` | rendimiento | `perf` | M | list invoca detalleRepo | (definir acción concreta) |
| 16 | `BACK-REND-012` | CRITICAL | `backend` | rendimiento | `perf` | M | cuando llega almacenId, hace inventarioRepo | (definir acción concreta) |
| 17 | `DB-ESC-001` | CRITICAL | `database` | escalabilidad | `perf` | M | **ninguna tabla ledger particionada** | PARTITION BY RANGE mensual con pg_partman |
| 18 | `FRONT-MAN-001` | CRITICAL | `frontend` | mantenibilidad | `regression` | M | [CR #7] — **sin test script, sin vitest/jest, sin @testing-library** | (definir acción concreta) |
| 19 | `DB-REND-005` | HIGH | `database` | rendimiento | `data_loss` | M | seg | (definir acción concreta) |
| 20 | `DB-REND-006` | HIGH | `database` | rendimiento | `data_loss` | M | inv | RANGE partition por creado_en mensual |
| 21 | `DB-REND-007` | HIGH | `database` | rendimiento | `data_loss` | M | ven | (definir acción concreta) |
| 22 | `DB-SEC-003` | HIGH | `database` | seguridad | `financial_fraud` | M | GRANT SELECT, INSERT, UPDATE, DELETE total a ferreteria_app sobre TODAS las tabl | segregar roles (ferreteria_ro, ferreteria_pos, ferreteria_admin) con GRANTs espe |
| 23 | `BACK-SEC-003` | HIGH | `backend` | seguridad | `security` | M | + ferreteriaDB/deploy/ | External Secrets / Vault / Sealed Secrets |
| 24 | `BACK-SEC-004` | HIGH | `backend` | seguridad | `security` | M | bootstrap admin admin / Admin123* (bcrypt 12) | trigger backend que fuerce cambio en primer login |
| 25 | `BACK-SEC-005` | HIGH | `backend` | seguridad | `security` | M | /actuator/** y Swagger UI son permitAll | lock down a ADMINISTRADOR o mover a puerto interno |
| 26 | `BACK-SEC-006` | HIGH | `backend` | seguridad | `security` | M | ACCESS_MINUTES default 480 (8 h) | bajar a 15 min, refresh rotativo |
| 27 | `BACK-SEC-007` | HIGH | `backend` | seguridad | `security` | M | /api/v1/auth/refresh y /logout sin @RateLimited | rate-limit ambos |
| 28 | `BACK-SEC-008` | HIGH | `backend` | seguridad | `security` | M | ninguna cabecera de seguridad (X-Content-Type-Options, X-Frame-Options, HSTS, Re | headers(...) en SecurityFilterChain |
| 29 | `BACK-SEC-009` | HIGH | `backend` | seguridad | `security` | M | ?sort= sin whitelist | whitelist por controller |
| 30 | `BACK-SEC-010` | HIGH | `backend` | seguridad | `security` | M | changePassword NO invalida los refresh tokens ya emitidos | gateway.revokeAllRefreshTokens(user.usuarioId()) post cambio |

> Total backlog: **316** hallazgos. Esta tabla muestra el top 30.

### §4.b Dependencias críticas (DAG resumido)

- `BACK-SEC-001` (`@PreAuthorize` en controllers) — bloquea cualquier refactor que asuma el modelo de autorización actual.
- `BACK-SEC-004` (quitar default `JWT_SECRET`) — bloquea `BACK-SEC-013` (fail-fast) y `BACK-REND-007` (Hikari+PgBouncer math).
- `DB-SCALE-001` (partitioning) — bloquea métricas P95/P99 estables para medir `BACK-REND-002` (N+1) y `BACK-REND-008` (`idx_ventas_fecha_local`).
- `FRONT-MAN-001` (vitest setup) — bloquea refactors frontend (`FRONT-REND-002`, `FRONT-UI-002`).

---

## §5 Quick wins (auto-derivados)

Filtro aplicado: `severity >= HIGH OR (MEDIUM)` AND `effort = S`. Total: **148** quick wins.

- **MEDIUM** `BACK-DIS-003` envelope {success, data, codigo, errorMessage, requestId, instance} no cumple RF — `common/error/GlobalExceptionHandler.java:67-79` — envelope {success, data, codigo, errorMessage, requestId, instance} no cumple RFC 7807 (type, title, status, detail, instance)
  - **Acción**: emitir ambos formatos o migrar a application/problem+json bajo /api/v2 _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-DIS-004` mezcla JPA repositories + JdbcTemplate + EntityManager + funciones PostgreSQL — `ven/service/VentaService.java:9-12` — mezcla JPA repositories + JdbcTemplate + EntityManager + funciones PostgreSQL. Acopla la API al esquema y dificulta testing sin Testcontainers
  - **Acción**: aislar tras un gateway _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-DIS-005` advice intercepta TODAS las respuestas; lista negra por path — `common/web/EnvelopeAdvice.java:42-66` — advice intercepta TODAS las respuestas; lista negra por path.startsWith(...) es frágil
  - **Acción**: whitelist _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-ESC-006` sin spring — `application.yml:73-77` — sin spring.jpa.properties.hibernate.generate_statistics=true + DataSource-Proxy.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `BACK-ESC-007` checkout no usa @Async — `ven/service/VentaService.java:101-110` — checkout no usa @Async. Para múltiples cajas simultáneas, considerar CQRS con eventos.
  - **Acción**: (definir acción concreta) _(blast_radius: `perf`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-005` 9 dependencias inyectadas — `ven/service/VentaService.java:51-52` — 9 dependencias inyectadas. SRP; extraer VentaFinanzas de VentaCheckout.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-006` cerrarTurno llama fn_cerrar_turno( — `fin/service/CajaService.java:159-161` — cerrarTurno llama fn_cerrar_turno(...); si falla, no se loguea motivo (caja sin ventas, diferencia excesiva)
  - **Acción**: mapear SQLSTATE a ErrorCode con DbErrorTranslator.translate _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-007` UUID — `common/web/RequestIdFilter.java:62-69` — UUID.fromString(incoming) lanza IllegalArgumentException capturado localmente. Pero LOG.info línea 51 corre ANTES de la validación, así que cliente con header inválido genera 2 logs.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-008` changePassword no invalida sesiones concurrentes (mismo usuario en 2 dispositivo — `seg/service/AuthService.java:81-91` — changePassword no invalida sesiones concurrentes (mismo usuario en 2 dispositivos).
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-009` translate recorre 15 niveles de causa; puede perder SQLException raíz en wrap pr — `common/error/DbErrorTranslator.java:39-50` — translate recorre 15 niveles de causa; puede perder SQLException raíz en wrap profundo.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-010` create consulta inventarioRepo — `inv/service/ConteoFisicoService.java:48-66` — create consulta inventarioRepo.findById(invId) para cada detalle; si un producto no tiene fila, cantidadSistema=0 silencioso
  - **Acción**: validar todos antes de continuar _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-EST-011` /actuator/metrics y /actuator/info abiertos sin restricción — `application.yml:54-58` — /actuator/metrics y /actuator/info abiertos sin restricción.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-001` JaCoCo gate global >=80% excluye **/repo/** — `build.gradle.kts:155-178` — JaCoCo gate global >=80% excluye **/repo/**. Excluir repos en un Spring Data JPA oculta regresiones en queries nativas. Considerar >=70% con gate explícito.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-002` tres métodos list/listByFechaLocal/getById con misma lógica — `ven/service/VentaService.java:30-42` — tres métodos list/listByFechaLocal/getById con misma lógica. Un solo método con Optional<Instant>.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

- **MEDIUM** `BACK-MAN-003` list() con 5 ramas if/else; usar Specification — `com/service/CompraService.java:62-77` — list() con 5 ramas if/else; usar Specification.
  - **Acción**: (definir acción concreta) _(blast_radius: `dx`; confidence: `HIGH`)_

---

## §6 Cobertura y metodología

Matriz de archivos revisados por proyecto. Definida en `audits/coverage.yaml`.

| Proyecto | Archivos totales (aprox) | Deep | Shallow | No revisados | Confianza |
|---|---:|---:|---:|---:|:--:|
| `backend` (ferreteriaBackend) | ~180 | ~25 | ~50 | ~105 | **MEDIUM** |
| `frontend` (ferreteriaFront) | ~120 | ~15 | ~10 | ~95 | **LOW** |
| `database` (ferreteriaDB) | 12 | 9 | 3 | 0 | **HIGH** |

### §6.b Áreas NO inspeccionadas (declaration of known gaps)

**ferreteriaBackend**:
- `src/main/java/mx/ferreteria/api/fis/**`
- `src/main/java/mx/ferreteria/api/cfg/**`
- `src/main/java/mx/ferreteria/api/seg/api/AuthController.java (partially)`

**ferreteriaFront**:
- `src/features/caja/CajaPage.tsx (957 LOC)`
- `src/features/caja/GastosPage.tsx (776 LOC)`
- `src/features/catalogo/PromocionesPage.tsx (937 LOC)`
- `src/features/catalogo/ClientesPage.tsx (422 LOC)`
- `src/features/inventario/**`
- `src/features/seguridad/**`
- `src/features/rrhh/**`
- `src/features/ventas/**`
- `src/features/fiscal/**`
- `src/features/compras/**`
- `src/lib/api/admin.ts`
- `src/lib/api/auditoria.ts`
- `src/lib/api/caja.ts`
- `src/lib/api/compras.ts`
- `src/lib/api/fis.ts`
- `src/lib/api/inventario.ts`
- `src/lib/api/promociones.ts`
- `src/lib/api/venta.ts`

---

## §7 Open Questions / Unknowns

Lo que inspeccionamos pero quedó incierto o necesita respuesta del equipo:

1. **Cardinalidad real de `seg.auditoria` por mes en producción.** Estimación 315 M filas/año, pero sin métricas reales.
2. **Endpoints con >100 RPS en producción.** No hay APM production-grade para validar los quick wins de performance.
3. **¿Hay WAF frente a la app?** (Cloudflare, AWS WAF, nginx mod_security). No visible en repo. Si sí, varios MEDIUM/LOW de seguridad son mitigados.
4. **¿Qué cubre el seguro de ciber-riesgo actual?** Afecta prioridades de remediación financiera.
5. **¿El equipo tiene on-call rotation y runbooks?** Afecta la prioridad de Resilience4j / circuit breakers.
6. **¿Hay OpenAPI/Swagger UI en producción?** Afecta lock-down de `/actuator/**` y `/swagger-ui/**`.
7. **¿Cuál es la política de LFPDPPP y retención fiscal (SAT)?** Afecta partitioning + archivado de `seg.auditoria` y `ven.ventas`.
8. **¿Qué archivos del frontend NO fueron leídos?** Listado explícito en `audits/coverage.yaml` §6.b. ~95 archivos no inspeccionados en `ferreteriaFront/`.

---

## §8 Apéndice — LOW backlog

Total LOW: **54** hallazgos. Mantener/eliminar/aplazar decisión documentada en cada fila cuando se revise.

| ID | Proyecto | Dimensión | Título | Acción |
|---|---|---|---|---|
| `BACK-DIS-006` | `backend` | diseno | service → service → repo cross-module; documentar con ADR | (definir acción concreta) |
| `BACK-DIS-007` | `backend` | diseno | loguea URI en cada request; revisar si filtra IDs/rutas internas | (definir acción concreta) |
| `BACK-EST-012` | `backend` | estabilidad | LOG | (definir acción concreta) |
| `BACK-EST-013` | `backend` | estabilidad | em | (definir acción concreta) |
| `BACK-EST-014` | `backend` | estabilidad | claveDe ThreadLocal no aplica en reactivo/async | (definir acción concreta) |
| `BACK-EST-015` | `backend` | estabilidad | checkout sin @Async; considerar CQRS con eventos | (definir acción concreta) |
| `BACK-MAN-011` | `backend` | mantenibilidad | Sort | (definir acción concreta) |
| `BACK-SEC-037` | `backend` | seguridad | single SecretKey sin soporte de rotación (ver CRITICAL #5) | (definir acción concreta) |
| `BACK-SEC-038` | `backend` | seguridad | sin MERGE/UPSERT | (definir acción concreta) |
| `BACK-SEC-039` | `backend` | seguridad | + — /auth/me devuelve email, telefono del empleado a cualquier usuario autentica | (definir acción concreta) |
| `BACK-SEC-040` | `backend` | seguridad | imágenes con tags flotantes (pgbouncer/pgbouncer:latest) | (definir acción concreta) |
| `BACK-SEC-041` | `backend` | seguridad | sin readOnlyRootFilesystem, allowPrivilegeEscalation: false, capabilities | (definir acción concreta) |
| `BACK-SEC-042` | `backend` | seguridad | sin securityContext | (definir acción concreta) |
| `BACK-SEC-043` | `backend` | seguridad | bucket por FQN controller; doc del burst | (definir acción concreta) |
| `BACK-SEC-044` | `backend` | seguridad | TokenResponse | (definir acción concreta) |
| `BACK-UI-007` | `backend` | ui_ux | List<Catalogo> sin Page | (definir acción concreta) |
| `BACK-UI-008` | `backend` | ui_ux | LOG | (definir acción concreta) |
| `BACK-UI-009` | `backend` | ui_ux | DEFAULT_MAX_SIZE=500 con N+1 latencia alta para /productos | (definir acción concreta) |
| `BACK-UI-010` | `backend` | ui_ux | /csrf-init devuelve 204 sin cuerpo | (definir acción concreta) |
| `DB-ESC-020` | `database` | escalabilidad | inv | (definir acción concreta) |
| `DB-ESC-021` | `database` | escalabilidad | idx_producto_impuesto_default parcial; documentar | (definir acción concreta) |
| `DB-ESC-022` | `database` | escalabilidad | log_min_duration_statement = 500 | (definir acción concreta) |
| `DB-EST-021` | `database` | estabilidad | DROP TRIGGER IF EXISTS + CREATE TRIGGER verboso (~30 bloques) | (definir acción concreta) |
| `DB-EST-022` | `database` | estabilidad | cfg | (definir acción concreta) |
| `DB-EST-023` | `database` | estabilidad | hard-coded mo | (definir acción concreta) |
| `DB-EST-024` | `database` | estabilidad | bootstrap replica sin validación post-pg_basebackup | (definir acción concreta) |
| `DB-MAN-015` | `database` | mantenibilidad | hard-coded motivos en fn_cerrar_turno | (definir acción concreta) |
| `DB-REND-021` | `database` | rendimiento | seg | (definir acción concreta) |
| `DB-REND-022` | `database` | rendimiento | v_uid INTEGER := NULLIF(current_setting('app | (definir acción concreta) |
| `DB-REND-023` | `database` | rendimiento | inv | (definir acción concreta) |
| `DB-REND-024` | `database` | rendimiento | inv | (definir acción concreta) |
| `DB-REND-025` | `database` | rendimiento | pgbouncer/pgbouncer:latest sin tag inmutable | (definir acción concreta) |
| `DB-SEC-018` | `database` | seguridad | postgres-exporter se conecta como postgres (superuser) | rol dedicado ferreteria_metrics |
| `DB-SEC-019` | `database` | seguridad | cat | CTE recursivo o trigger |
| `DB-SEC-020` | `database` | seguridad | seg | (definir acción concreta) |
| `DB-UI-005` | `database` | ui_ux | seg | columna modulo VARCHAR(10) separada |
| `DB-UI-006` | `database` | ui_ux | vw_mejores_dias_venta decodifica EXTRACT(ISODOW) con CASE | cat.dias_semana JOIN |
| `DB-UI-007` | `database` | ui_ux | notas/observaciones libres sin formato | (definir acción concreta) |
| `DB-UI-008` | `database` | ui_ux | telefono VARCHAR(20) sin formato | (definir acción concreta) |
| `FRONT-ACC-007` | `frontend` | accesibilidad | inputs type="date" sin aria-label propio | (definir acción concreta) |
| `FRONT-ACC-008` | `frontend` | accesibilidad | botón tema cambia icono sin anunciar estado (aria-pressed) | (definir acción concreta) |
| `FRONT-ACC-009` | `frontend` | accesibilidad | SweetAlert2 provee aria-live; loading() no anuncia "Cargando…" dinámico | (definir acción concreta) |
| `FRONT-ACC-010` | `frontend` | accesibilidad | sin eslint-plugin-jsx-a11y | (definir acción concreta) |
| `FRONT-DIS-002` | `frontend` | diseno | header con border-b aunque no haya título | (definir acción concreta) |
| `FRONT-DIS-003` | `frontend` | diseno | botón "Cambiar contraseña" hardcodeado en español en lugar de t("appshell | (definir acción concreta) |
| `FRONT-ESC-004` | `frontend` | escalabilidad | MOTIVOS_MOVIMIENTO, TIPOS_PRODUCTO, FORMAS_PAGO, PUESTOS, TIPOS_GASTO hardcodead | (definir acción concreta) |
| `FRONT-EST-006` | `frontend` | estabilidad | puedeRefrescar falla si path incluye query string o encoding | (definir acción concreta) |
| `FRONT-EST-007` | `frontend` | estabilidad | listeners de error/unhandledrejection nunca se desregistran; sin e | (definir acción concreta) |
| `FRONT-MAN-009` | `frontend` | mantenibilidad | ayer calculado en módulo; si vive >24h, stale | (definir acción concreta) |
| `FRONT-REND-009` | `frontend` | rendimiento | effect auto-add depende de agregar que cambia cada render | (definir acción concreta) |
| `FRONT-REND-010` | `frontend` | rendimiento | sin virtualización para tablas grandes | (definir acción concreta) |
| `FRONT-SEC-007` | `frontend` | seguridad | this | (definir acción concreta) |
| `FRONT-UI-008` | `frontend` | ui_ux | badge "código" no comunica auto-add | (definir acción concreta) |
| `FRONT-UI-009` | `frontend` | ui_ux | topbar móvil sin usuario ni accesos rápidos | (definir acción concreta) |

---

## §9 Próximos pasos (esperando aprobación)

Este archivo es **render derivado** de `audits/findings.yaml`. **No se modificó código** en este run.

Cuando aprueben, los siguientes scripts y comandos están listos:

1. `python3 scripts/parse_audit.py MEJORAS_ECC.md audits/findings.yaml` — re-extraer hallazgos.
2. `python3 scripts/validate.py audits/findings.yaml` — verificar unicidad, refs, esquema.
3. `python3 scripts/render.py` — regenerar este documento.
4. **Aplicar quick wins** (§5) como primer commit aislado.
5. **Refactorizar N+1** en `VentaService.toResponse` (BACK-REND-001) — commit aislado.
6. **`@PreAuthorize`** en todos los controllers (BACK-SEC-001) — commit grande.
7. **Segregación de roles DB** + `REVOKE DELETE` en ledger — migración versionada.
8. **Partitioning** de `seg.auditoria` y ledgers — migración con ventana de mantenimiento.

**Esperando aprobación para empezar a modificar código.** Indica qué fase priorizar.
