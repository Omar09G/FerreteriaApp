-- ============================================================================
-- V22__informe_dashboard.sql
-- Informe diario del dashboard (KPIs + cierre): amplía
-- notif.notificacion_jobs con tipo INFORME_DASHBOARD / ref INFORME
-- (un registro por día: ref_id = epoch day del fin del rango).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_informe_dashboard.sql.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME'));
