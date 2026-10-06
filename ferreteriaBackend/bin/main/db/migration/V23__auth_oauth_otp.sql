-- ============================================================================
-- V23__auth_oauth_otp.sql
-- Login con Google (redirect) + segundo factor OTP obligatorio por
-- email/WhatsApp: vínculo OAuth en seg.usuarios y tabla de desafíos OTP
-- de un solo uso (TTL 5 min, máx 5 intentos).
-- Idempotente. Espejo: scripts/02_tablas.sql (columnas + tabla) y
-- migrations/delta_auth_oauth_otp.sql.
-- ============================================================================

ALTER TABLE seg.usuarios
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(10) NOT NULL DEFAULT 'local'
        CHECK (auth_provider IN ('local', 'google')),
    ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS email_verificado_en TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS seg.otp_desafios (
    otp_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    challenge_id VARCHAR(64) NOT NULL UNIQUE,
    usuario_id   INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id) ON DELETE CASCADE,
    proposito    VARCHAR(16) NOT NULL DEFAULT 'login'
                 CHECK (proposito IN ('login')),
    canal        VARCHAR(16) NOT NULL DEFAULT 'email'
                 CHECK (canal IN ('email', 'whatsapp')),
    codigo_hash  VARCHAR(100),
    intentos     INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    expira_en    TIMESTAMPTZ NOT NULL DEFAULT now() + interval '5 minutes',
    enviado_en   TIMESTAMPTZ,
    consumido_en TIMESTAMPTZ,
    revocado_en  TIMESTAMPTZ,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_otp_usuario
    ON seg.otp_desafios(usuario_id, proposito, creado_en DESC);
