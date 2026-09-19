-- ============================================================================
-- V18__check_fotos_url.sql
-- Valida formato de fotos de entidades: foto_url/imagen_url solo aceptan
-- URL pública https? (MinIO) o data:image/. Espejo en scripts/02_tablas.sql
-- (CHECK inline en la definición de columna) y
-- ferreteriaDB/migrations/delta_check_fotos_url.sql.
-- Idempotente: solo agrega el CONSTRAINT si no existe (pg_constraint).
-- NULL siempre permitido (columnas opcionales).
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
