-- ============================================================================
-- DELTA: valida formato de fotos de entidades (clientes, proveedores,
-- empleados, productos). Solo URL pública https? (MinIO) o data:image/.
-- Idempotente: solo agrega el CONSTRAINT si no existe.
-- También integrado en scripts/02_tablas.sql y migración V18 del backend.
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_proveedores_foto_url') THEN
        ALTER TABLE com.proveedores
            ADD CONSTRAINT chk_proveedores_foto_url
            CHECK (foto_url IS NULL OR foto_url ~ '^https?://|^data:image/');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_empleados_foto_url') THEN
        ALTER TABLE rh.empleados
            ADD CONSTRAINT chk_empleados_foto_url
            CHECK (foto_url IS NULL OR foto_url ~ '^https?://|^data:image/');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_clientes_foto_url') THEN
        ALTER TABLE ven.clientes
            ADD CONSTRAINT chk_clientes_foto_url
            CHECK (foto_url IS NULL OR foto_url ~ '^https?://|^data:image/');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_productos_imagen_url') THEN
        ALTER TABLE inv.productos
            ADD CONSTRAINT chk_productos_imagen_url
            CHECK (imagen_url IS NULL OR imagen_url ~ '^https?://|^data:image/');
    END IF;
END
$$;
