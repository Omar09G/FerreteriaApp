-- ============================================================================
-- DELTA: notificacion_jobs + pdf_url en ventas + whatsapp en empleados
-- Idempotente. También en scripts/01_base_esquemas.sql, scripts/02_tablas.sql
-- y migración V21 del backend.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS notif;

CREATE TABLE IF NOT EXISTS notif.notificacion_jobs (
    job_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo         VARCHAR(32) NOT NULL
                 CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA')),
    ref_tipo     VARCHAR(16) NOT NULL
                 CHECK (ref_tipo IN ('VENTA','NOMINA')),
    ref_id       BIGINT NOT NULL,
    estado       VARCHAR(16) NOT NULL DEFAULT 'PENDIENTE'
                 CHECK (estado IN ('PENDIENTE','PROCESANDO','ENVIADA','ERROR')),
    pdf_url      TEXT,
    intentos     INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    ultimo_error TEXT,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en   TIMESTAMPTZ,
    CONSTRAINT uq_notif_job_ref UNIQUE (tipo, ref_id)
);

CREATE INDEX IF NOT EXISTS idx_notif_jobs_estado
    ON notif.notificacion_jobs(estado, creado_en);

ALTER TABLE ven.ventas ADD COLUMN IF NOT EXISTS pdf_url TEXT;
ALTER TABLE rh.empleados ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(20);

-- La app opera como ferreteria_app (vía PgBouncer): acceso al schema nuevo.
GRANT USAGE ON SCHEMA notif TO ferreteria_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON notif.notificacion_jobs TO ferreteria_app;
ALTER TABLE notif.notificacion_jobs OWNER TO ferreteria_app;
