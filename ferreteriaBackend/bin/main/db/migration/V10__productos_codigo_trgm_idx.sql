-- ============================================================================
-- V10: Índice trigram para búsqueda por codigo de producto (BACK-ESC-005)
-- ============================================================================
-- ProductoRepository.findByCodigoContainingIgnoreCase y
-- findByActivoTrueAndNombreContainingIgnoreCase hacen LIKE %termino%. Sin
-- trigram, Postgres hace SeqScan sobre inv.productos (~100k SKUs típicos
-- ferreteros), degradando la busqueda por codigo de barras a >100 ms.
-- El indice idx_productos_nombre_trgm ya cubre nombre; añadimos codigo.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_productos_codigo_trgm
    ON inv.productos USING GIN (codigo gin_trgm_ops);

ANALYZE inv.productos;
