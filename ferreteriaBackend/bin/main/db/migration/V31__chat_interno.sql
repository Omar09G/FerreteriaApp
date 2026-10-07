-- ============================================================================
-- V31__chat_interno.sql
-- Chat interno 1 a 1 y por grupos entre usuarios del sistema. Los mensajes
-- nuevos viajan en tiempo real por el mismo stream SSE de la bandeja
-- (tipo CHAT_MENSAJE). Sin FK a seg.usuarios en cascada para mensajes:
-- el historial se conserva aunque el usuario se elimine (autor_id queda
-- como referencia).
-- Idempotente. Espejo: scripts/02_tablas.sql y migrations/delta_chat_interno.sql.
-- ============================================================================

CREATE TABLE IF NOT EXISTS notif.chat_conversacion (
    conversacion_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(16) NOT NULL
                   CHECK (tipo IN ('DIRECTA','GRUPO')),
    titulo         VARCHAR(120),
    creada_por     INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creada_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notif.chat_participante (
    participante_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversacion_id  BIGINT NOT NULL
                     REFERENCES notif.chat_conversacion(conversacion_id) ON DELETE CASCADE,
    usuario_id       INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id) ON DELETE CASCADE,
    ultimo_leido_en  TIMESTAMPTZ,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_participante UNIQUE (conversacion_id, usuario_id)
);
CREATE INDEX IF NOT EXISTS idx_chat_participante_usuario
    ON notif.chat_participante(usuario_id, conversacion_id);

CREATE TABLE IF NOT EXISTS notif.chat_mensaje (
    mensaje_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversacion_id BIGINT NOT NULL
                    REFERENCES notif.chat_conversacion(conversacion_id) ON DELETE CASCADE,
    autor_id        INTEGER NOT NULL,
    cuerpo          TEXT NOT NULL CHECK (char_length(cuerpo) BETWEEN 1 AND 2000),
    creada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminada_en    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_chat_mensaje_conversacion
    ON notif.chat_mensaje(conversacion_id, creada_en DESC);

ALTER TABLE notif.chat_conversacion OWNER TO ferreteria_app;
ALTER TABLE notif.chat_participante OWNER TO ferreteria_app;
ALTER TABLE notif.chat_mensaje OWNER TO ferreteria_app;
