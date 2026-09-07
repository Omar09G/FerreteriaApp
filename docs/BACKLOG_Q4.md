# Backlog Q4 — Top 10 priorizado (post-audit 316/316)

Priorizado por impacto × esfuerzo. Todos MEDIUM/LOW, no bloqueantes con mitigaciones actuales.

| # | ID | Título | Impacto | Esfuerzo | Acción |
|---|---|---|---|---|---|
| 1 | BACK-ESC-001 | Rate-limit Redis distributed | HIGH perf multi-replica | M (bucket4j-redis + spring-data-redis) | Activar `distributed=true` + `RATE_LIMIT_REDIS_URI` |
| 2 | BACK-DIS-001 | Resolver ciclo cat↔inv | MEDIUM arch | M (extraer `ProductoService→InventarioService` via gateway) | ArchUnit `modulosSinCiclos` FAIL → gateway |
| 3 | DB-EST-001/003 | Triggers STATEMENT si ventas masivas >100 líneas | MEDIUM | M (reescribir `fn_recalc_totales_venta` + tests) | Solo si hot-path |
| 4 | FRONT-ACC-001 follow-up | a11y audit completo (axe) | MEDIUM | S (axe-core + eslint-jsx-a11y) | `npx axe` en E2E |
| 5 | BACK-SEC-022 | Username enumeration timing | MEDIUM sec | S (constant-time response) | Uniformizar tiempo login fail |
| 6 | DB-REND cerrar_turno | Covering index si >5 cierres/hora | LOW | S (covering `turno_caja_id, concepto` INCLUDE monto) | Solo si hot-path |
| 7 | BACK-MAN docs | ADR para PK BIGINT vs INTEGER | LOW | XS (ADR) | `docs/adr/001-pk-convention.md` |
| 8 | FRONT-MAN i18n | Completar claves faltantes (axe) | LOW | S | `npm run i18n:check` |
| 9 | DB-SEC-007 hardening | Restringir pg_hba a 10.0.0.0/8 | LOW | S (ConfigMap patch RFC1918) | Defensa en profundidad |
| 10 | BACK-REND-025 | TaskDecorator si @Async futuro | LOW | XS (decorator) | Solo si se introduce async |

## Criterio
- No bloqueante para prod con mitigaciones actuales (NetworkPolicy, scram-sha-256, fail-closed flags).
- Estimación total: 2 sprints (4 semanas) con 2 devs.

## Verificación
- Re-ejecutar `python3 scripts/validate.py audits/findings.yaml` tras cada sprint.
- Mantener `v0.9.0-audit-100pct` como baseline; tag `v1.0.0` tras cerrar top 5.
