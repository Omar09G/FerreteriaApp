-- Incremento forward-only M6: fis.facturas (CFDI persistido para consulta).
-- generateMigrations regenera solo V1/V2; este incremento se escribe a mano
-- reflejando la adición canónica en ../ferreteriaDB/scripts/02_tablas.sql (sección H).
-- Idempotente: aplicar sobre BD ya migrada o vacía es seguro.
CREATE TABLE IF NOT EXISTS fis.facturas (
    factura_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(10) NOT NULL CHECK (tipo IN ('EMITIDA','RECIBIDA')),
    serie          VARCHAR(20),
    folio          VARCHAR(40) NOT NULL,
    uuid           VARCHAR(64) UNIQUE,
    emisor_rfc     VARCHAR(13) NOT NULL,
    receptor_rfc   VARCHAR(13) NOT NULL,
    subtotal       NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva            NUMERIC(14,2) NOT NULL DEFAULT 0,
    total          NUMERIC(14,2) GENERATED ALWAYS AS (subtotal + iva) STORED,
    fecha_timbrado TIMESTAMPTZ NOT NULL DEFAULT now(),
    cfdi_xml       TEXT,
    estado         VARCHAR(12) NOT NULL DEFAULT 'ACTIVA'
                   CHECK (estado IN ('ACTIVA','CANCELADA')),
    venta_id       BIGINT,
    usuario_id     INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_factura_totales CHECK (subtotal >= 0 AND iva >= 0)
);

CREATE INDEX IF NOT EXISTS idx_facturas_tipo_fecha ON fis.facturas(tipo, fecha_timbrado DESC);
CREATE INDEX IF NOT EXISTS idx_facturas_venta ON fis.facturas(venta_id)
    WHERE venta_id IS NOT NULL;

GRANT SELECT, INSERT, UPDATE ON fis.facturas TO ferreteria_app;
GRANT USAGE ON SEQUENCE fis.facturas_factura_id_seq TO ferreteria_app;

COMMENT ON TABLE fis.facturas IS
'CFDI emitidos/recibidos persistidos para consulta. Timbrado PAC queda como integración futura (M6/M7).';