-- ============================================================================
-- DELTA: Hardening seg.usuarios para brute-force protection (PASO 28)
-- Añade columnas para lockout tras N intentos fallidos.
-- ============================================================================

ALTER TABLE seg.usuarios
    ADD COLUMN IF NOT EXISTS debe_cambiar_password BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMPTZ;
