-- ============================================================================
-- V28__turno_abierto_job.sql
-- Aviso nocturno de turnos abiertos (corte sin cerrar) a GERENTES y
-- ADMINISTRADORES: amplía notif.notificacion_jobs con tipo TURNO_ABIERTO /
-- ref TURNO (un registro por día: ref_id = epoch day).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_turno_abierto_job.sql.
-- ============================================================================

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_tipo_check
    CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR',
                   'COBRANZA','RENTAS','STOCK_BAJO','TURNO_ABIERTO'));

ALTER TABLE notif.notificacion_jobs DROP CONSTRAINT IF EXISTS notificacion_jobs_ref_tipo_check;
ALTER TABLE notif.notificacion_jobs
    ADD CONSTRAINT notificacion_jobs_ref_tipo_check
    CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS','COBRANZA','RENTAS','STOCK',
                       'TURNO'));
