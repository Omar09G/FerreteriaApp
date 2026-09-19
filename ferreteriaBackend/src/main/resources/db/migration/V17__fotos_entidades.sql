-- ============================================================================
-- V17__fotos_entidades.sql
-- Fotos de entidades: agrega foto_url a clientes, proveedores y empleados.
-- inv.productos ya tiene imagen_url (02_tablas); aquí solo se cubren las
-- tres tablas que no la tenían. Las vistas del dashboard usan listas
-- explícitas de columnas, por lo que no requieren recreación.
-- Idempotente: ADD COLUMN IF NOT EXISTS. Espejo en scripts/02_tablas.sql
-- y ferreteriaDB/migrations/delta_fotos_entidades.sql.
-- El backend renombra cada archivo a UUID + extensión original al subirlo
-- a MinIO y guarda aquí la URL pública resultante.
-- ============================================================================

ALTER TABLE com.proveedores ADD COLUMN IF NOT EXISTS foto_url TEXT;
ALTER TABLE rh.empleados   ADD COLUMN IF NOT EXISTS foto_url TEXT;
ALTER TABLE ven.clientes    ADD COLUMN IF NOT EXISTS foto_url TEXT;
