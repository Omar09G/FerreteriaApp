-- ============================================================================
-- V27__productos_nombre_lower_trgm.sql
-- Búsqueda difusa del POS (GET /productos/buscar): índice trigram sobre
-- lower(nombre) para el operador word_similarity (<%), que tolera typos
-- de mostrador ("torni" -> "Tornillo 3/8"). El índice existente
-- idx_productos_nombre_trgm cubre el caso sensible a mayúsculas; este cubre
-- la comparación insensible que usa el buscador.
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_productos_nombre_lower_trgm.sql.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_productos_nombre_lower_trgm
    ON inv.productos USING GIN (lower(nombre) gin_trgm_ops);

ANALYZE inv.productos;
