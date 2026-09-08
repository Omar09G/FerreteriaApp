-- ============================================================================
-- V9: Índice parcial para findBajoStock() (BACK-REND-023)
-- ============================================================================
-- La query `SELECT ... WHERE stock <= stock_minimo` hace SeqScan sobre
-- inv.inventario cuando no hay filtro por almacen_id. Un índice parcial sobre
-- la condicion real (`stock <= stock_minimo`) es optimo:
--   - tamaño: solo filas que SI son candidatas a "bajo stock"
--   - selectivity: alto (pocos productos debajo del minimo)
--   - cubre el predicado del WHERE sin necesidad de visitar la tabla
--
-- Partial index idempotente; backfill automatico via CONCURRENTLY no es valido
-- dentro de una transaccion de Flyway, por lo que se crea normal. En produccion
-- con tabla > 10M filas, considerar crear manualmente con CONCURRENTLY en
-- ventana de mantenimiento.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_inventario_bajo_stock
    ON inv.inventario(producto_id, almacen_id)
    WHERE stock <= stock_minimo;

ANALYZE inv.inventario;
