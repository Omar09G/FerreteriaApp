-- ============================================================================
-- V30__bandeja_notificaciones.sql
-- Bandeja de notificaciones en tiempo real (SSE): una fila por destinatario
-- y evento. El usuario conectado la recibe al instante por el stream SSE;
-- el desconectado la ve como contador + historial al entrar.
-- Tipos: los 8 jobs existentes + eventos de dominio (venta cancelada,
-- compra creada, apertura/corte de caja, nómina creada) + CHAT_MENSAJE.
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_bandeja_notificaciones.sql.
-- Purga: filas con más de 90 días se eliminan (ver BandejaService.purgar).
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
