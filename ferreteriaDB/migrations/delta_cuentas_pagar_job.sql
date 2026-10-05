-- ============================================================================
-- delta_cuentas_pagar_job.sql
-- Espejo de V24__cuentas_pagar_job.sql del backend (fuente: scripts/02_tablas.sql).
-- Recordatorio diario de cuentas por pagar: tipo CUENTAS_PAGAR / ref CUENTAS
-- en notif.notificacion_jobs (un registro por día).
-- Idempotente.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS'));
