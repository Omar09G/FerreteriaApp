-- ============================================================================
-- V24__cuentas_pagar_job.sql
-- Recordatorio diario de cuentas por pagar (vencidas + pendientes) a
-- GERENTES y ADMINISTRADORES: amplía notif.notificacion_jobs con tipo
-- CUENTAS_PAGAR / ref CUENTAS (un registro por día: ref_id = epoch day).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_cuentas_pagar_job.sql.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS'));
