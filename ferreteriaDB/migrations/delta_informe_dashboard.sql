-- ============================================================================
-- DELTA: informe diario del dashboard en notificacion_jobs
-- (tipo INFORME_DASHBOARD / ref INFORME, un registro por día).
-- Idempotente. También en scripts/02_tablas.sql y migración V22 del backend.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME'));
