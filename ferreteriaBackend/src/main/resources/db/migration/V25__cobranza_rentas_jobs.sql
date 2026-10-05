-- ============================================================================
-- V25__cobranza_rentas_jobs.sql
-- Recordatorios diarios de cobranza (vencidas + pendientes) y rentas
-- (vencidas + próximas a devolver) a GERENTES y ADMINISTRADORES: amplía
-- notif.notificacion_jobs con tipos COBRANZA/RENTAS y refs COBRANZA/RENTAS
-- (un registro por día: ref_id = epoch day).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_cobranza_rentas_jobs.sql.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR',
                   'COBRANZA','RENTAS'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS','COBRANZA','RENTAS'));
