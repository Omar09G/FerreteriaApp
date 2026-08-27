-- ============================================================================
-- DELTA legado: seg.refresh_tokens (backend M1) para BDs provisionadas antes
-- de la integracion con Flyway. Idempotente. En installs nuevos ya viene en V2.
-- ============================================================================
CREATE TABLE IF NOT EXISTS seg.refresh_tokens (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id) ON DELETE CASCADE,
    token_hash       VARCHAR(100) NOT NULL UNIQUE,
    expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at       TIMESTAMPTZ,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_usuario ON seg.refresh_tokens(usuario_id);
