-- ============================================================================
-- V26__stock_bajo_job.sql
-- Recordatorio diario de stock bajo a GERENTES y ADMINISTRADORES: amplía
-- notif.notificacion_jobs con tipo STOCK_BAJO / ref STOCK (un registro por
-- día: ref_id = epoch day).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_stock_bajo_job.sql.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR',
                   'COBRANZA','RENTAS','STOCK_BAJO'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS','COBRANZA','RENTAS','STOCK'));
