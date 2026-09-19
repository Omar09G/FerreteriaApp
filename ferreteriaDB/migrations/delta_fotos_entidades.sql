-- ============================================================================
-- DELTA: fotos de entidades (clientes, proveedores, empleados)
-- Agrega foto_url TEXT a las tres tablas. inv.productos ya tiene imagen_url.
-- Idempotente: ADD COLUMN IF NOT EXISTS.
-- También integrado en scripts/02_tablas.sql y migración V17 del backend.
-- ============================================================================

ALTER TABLE com.proveedores ADD COLUMN IF NOT EXISTS foto_url TEXT;
ALTER TABLE rh.empleados   ADD COLUMN IF NOT EXISTS foto_url TEXT;
ALTER TABLE ven.clientes    ADD COLUMN IF NOT EXISTS foto_url TEXT;
