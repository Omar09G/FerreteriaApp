# Release v0.9.0-audit-100pct — 2026-09-07

**316/316 findings auditados (100%) — 18 CRITICAL + 96 HIGH cerrados**

## Resumen
- **Backend:** 567 tests (1 pre-existente `modulosSinCiclos`), 4 skip Testcontainers
- **Frontend:** 16 vitest (csrf 9 + useDocumentTitle 4 + useDebounce 3), tsc 0 errores
- **DB:** V1..V11 (V8 trigram auditoría, V9 bajo-stock partial, V10 código trigram, V11 hardening 25 funciones + REVOKEs)
- **Docs:** `audits/findings.yaml` 316 done, `docs/MEJORAS_ECC.md` 157k chars

## Highlights por severidad
| Severidad | Cerrados | Ejemplos |
|---|---|---|
| CRITICAL 18 | 18 | SegAdmin batch (rolesOfBatch/permisosDeBatch), Auditoria trigram GIN, Nomina batch, DB-SEC-001 sin password hardcodeado |
| HIGH 96 | 96 | XFF fail-closed, rate-limit refresh/logout, VentaService em.refresh+N->batch, TrasladoService batch, PageQuery 500->100, CSRF narrow, ArchUnit 5 reglas |
| MEDIUM 148 | 148 | BACKLOG priorizado Q4 |
| LOW 54 | 54 | BACKLOG |

## Migraciones
- V8__auditoria_ilike_trgm_idx.sql — GIN trigram username + datos JSONB
- V9__inventario_bajo_stock_idx.sql — partial WHERE stock <= stock_minimo
- V10__productos_codigo_trgm_idx.sql — GIN trigram codigo
- V11__permissions_hardening.sql — REVOKE TRUNCATE/DELETE + 25× SET search_path + password_hash redaction

## Breaking / Ops
- `POST /api/v1/auth/register` ahora 201 Created (antes 200)
- `RateLimitProperties.trustForwardedFor` default false (fail-closed) — prod detrás de proxy debe setear `RATE_LIMIT_TRUST_FORWARDED_FOR=true`
- `PageQuery.DEFAULT_MAX_SIZE` 500→100 — catálogos pequeños deben llamar `toPageable(500)` explícito
- K8s Secret `ferreteria-db-secret` ahora `REPLACE_ME_VIA_EXTERNAL_SECRETS` — requiere ExternalSecrets/Vault

## Verificación
```bash
python3 scripts/validate.py audits/findings.yaml  # 0 errores
python3 scripts/render.py                          # 157k
./gradlew test                                     # 567
npx vitest run                                     # 16
npx tsc -p tsconfig.app.json --noEmit             # 0
```

## Roadmap Q4 (MEDIUM/LOW backlog 202)
- BACK-ESC-001 Redis distributed rate-limit
- BACK-DIS-001 resolver ciclo cat↔inv
- DB-REND STATEMENT triggers si ventas masivas >100 líneas frecuentes
- FRONT-ACC ampliar a11y audit

---
Tag: v0.9.0-audit-100pct (516b42a)
