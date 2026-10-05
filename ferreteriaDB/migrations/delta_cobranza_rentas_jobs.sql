-- ============================================================================
-- delta_cobranza_rentas_jobs.sql
-- Espejo de V25__cobranza_rentas_jobs.sql del backend (fuente: scripts/02_tablas.sql).
-- Recordatorios diarios de cobranza y rentas: tipos COBRANZA/RENTAS y refs
-- COBRANZA/RENTAS en notif.notificacion_jobs (un registro por día).
-- Idempotente.
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
