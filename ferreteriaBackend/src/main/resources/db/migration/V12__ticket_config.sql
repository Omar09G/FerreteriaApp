-- ============================================================================
-- V12__ticket_config.sql
-- Configuración parametrizable del ticket de venta (imagen de referencia).
-- Singleton global (almacen_id IS NULL) + override opcional por almacén.
-- Idempotente.
-- ============================================================================

CREATE TABLE IF NOT EXISTS cfg.ticket_config (
    ticket_config_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_id            INTEGER REFERENCES inv.almacenes(almacen_id) ON DELETE CASCADE,
    -- Encabezado
    logotipo_url          TEXT CHECK (logotipo_url IS NULL OR logotipo_url ~ '^https?://|^data:image/'),
    mostrar_logotipo      BOOLEAN NOT NULL DEFAULT false,
    nombre_negocio        VARCHAR(180) NOT NULL DEFAULT 'Ferretería El Tornillo Feliz',
    direccion             VARCHAR(250),
    cp                    VARCHAR(10),
    rfc                   VARCHAR(13) CHECK (rfc IS NULL OR rfc ~* '^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][0-9A-Z]{2}$'),
    telefono              VARCHAR(20) CHECK (telefono IS NULL OR telefono ~ '^[+0-9 ()-]{7,20}$'),
    email                 VARCHAR(120) CHECK (email IS NULL OR email ~* '^[^@]+@[^@]+\.[^@]+$'),
    sitio_web             VARCHAR(120),
    -- Cuerpo
    titulo_documento      VARCHAR(40) NOT NULL DEFAULT 'Factura simplificada',
    mostrar_datos_cliente BOOLEAN NOT NULL DEFAULT true,
    mostrar_numero_factura BOOLEAN NOT NULL DEFAULT true,
    mostrar_caja          BOOLEAN NOT NULL DEFAULT true,
    mostrar_fecha_hora    BOOLEAN NOT NULL DEFAULT true,
    mostrar_vendedor      BOOLEAN NOT NULL DEFAULT true,
    mostrar_desglose_iva  BOOLEAN NOT NULL DEFAULT true,
    mostrar_descuento     BOOLEAN NOT NULL DEFAULT true,
    mostrar_cambio        BOOLEAN NOT NULL DEFAULT true,
    -- Pie
    mensaje_pie           TEXT DEFAULT '30 DÍAS PARA DEVOLUCIONES O CAMBIOS',
    pie_secundario        TEXT,
    ancho_papel_mm        SMALLINT NOT NULL DEFAULT 80 CHECK (ancho_papel_mm IN (58, 80)),
    font_size_pt          SMALLINT NOT NULL DEFAULT 9 CHECK (font_size_pt BETWEEN 7 AND 12),
    actualizado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por       INTEGER REFERENCES seg.usuarios(usuario_id),
    CONSTRAINT uq_ticket_config_almacen UNIQUE (almacen_id)
);

-- Índice parcial para garantizar único global en PG14 (UNIQUE con NULL no distingue en PG14)
CREATE UNIQUE INDEX IF NOT EXISTS uq_ticket_config_global_null ON cfg.ticket_config((almacen_id IS NULL)) WHERE almacen_id IS NULL;

-- Seed global si no existe: toma nombre_negocio de cfg.configuracion
INSERT INTO cfg.ticket_config (almacen_id, nombre_negocio, direccion, rfc, telefono, sitio_web, mensaje_pie, pie_secundario)
SELECT
    NULL,
    COALESCE((SELECT valor FROM cfg.configuracion WHERE clave='nombre_negocio'), 'Ferretería El Tornillo Feliz'),
    NULL, NULL, NULL, NULL,
    '30 DÍAS PARA DEVOLUCIONES O CAMBIOS',
    NULL
WHERE NOT EXISTS (SELECT 1 FROM cfg.ticket_config WHERE almacen_id IS NULL);

-- Trigger updated_at
CREATE OR REPLACE FUNCTION cfg.fn_ticket_config_touch() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = pg_catalog, public, cfg AS $$
BEGIN NEW.actualizado_en := now(); RETURN NEW; END $$;

DROP TRIGGER IF EXISTS trg_ticket_config_touch ON cfg.ticket_config;
CREATE TRIGGER trg_ticket_config_touch BEFORE UPDATE ON cfg.ticket_config
FOR EACH ROW EXECUTE FUNCTION cfg.fn_ticket_config_touch();
