-- ============================================================================
-- delta_bandeja_notificaciones.sql
-- Espejo de V30__bandeja_notificaciones.sql del backend (fuente: scripts/02_tablas.sql).
-- Bandeja de notificaciones en tiempo real (SSE): una fila por destinatario
-- y evento. Idempotente.
-- ============================================================================

CREATE TABLE IF NOT EXISTS notif.notificacion_bandeja (
    bandeja_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id) ON DELETE CASCADE,
    tipo         VARCHAR(32) NOT NULL
                 CHECK (tipo IN ('VENTA_TICKET','NOMINA_PAGADA','INFORME_DASHBOARD','CUENTAS_PAGAR',
                                'COBRANZA','RENTAS','STOCK_BAJO','TURNO_ABIERTO',
                                'VENTA_CANCELADA','COMPRA_CREADA','TURNO_APERTURA','CORTE_CAJA',
                                'NOMINA_CREADA','CHAT_MENSAJE')),
    titulo       VARCHAR(140) NOT NULL,
    detalle      TEXT,
    ref_tipo     VARCHAR(16) NOT NULL
                 CHECK (ref_tipo IN ('VENTA','NOMINA','INFORME','CUENTAS','COBRANZA','RENTAS','STOCK',
                                    'TURNO','COMPRA','CORTE','CHAT')),
    ref_id       BIGINT NOT NULL,
    leida_en     TIMESTAMPTZ,
    creada_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bandeja_usuario_ref UNIQUE (usuario_id, tipo, ref_tipo, ref_id)
);

CREATE INDEX IF NOT EXISTS idx_bandeja_usuario
    ON notif.notificacion_bandeja(usuario_id, leida_en, creada_en DESC);

-- Convención del módulo (igual que delta_notificacion_jobs.sql): las tablas
-- notif pertenecen al rol de aplicación (el esquema no está en los GRANTs
-- globales de 02_tablas.sql, que cubren cat/cfg/rh/seg/inv/com/ven/fin/fis).
ALTER TABLE notif.notificacion_bandeja OWNER TO ferreteria_app;
