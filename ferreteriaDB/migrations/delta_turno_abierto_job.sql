-- ============================================================================
-- delta_turno_abierto_job.sql
-- Espejo de V28__turno_abierto_job.sql del backend (fuente: scripts/02_tablas.sql).
-- Aviso nocturno de turnos abiertos: tipo TURNO_ABIERTO / ref TURNO en
-- notif.notificacion_jobs (un registro por día).
-- Idempotente.
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
