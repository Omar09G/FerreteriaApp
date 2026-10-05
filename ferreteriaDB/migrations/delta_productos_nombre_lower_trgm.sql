-- ============================================================================
-- delta_productos_nombre_lower_trgm.sql
-- Espejo de V27__productos_nombre_lower_trgm.sql del backend (fuente:
-- scripts/02_tablas.sql). Índice trigram sobre lower(nombre) para la
-- búsqueda difusa del POS (operador word_similarity <%).
-- Idempotente.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_productos_nombre_lower_trgm
    ON inv.productos USING GIN (lower(nombre) gin_trgm_ops);

ANALYZE inv.productos;
