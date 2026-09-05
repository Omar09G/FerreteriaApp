-- ============================================================================
-- DELTA: Índices para filtros por fecha_local en ventas
-- Añade índices sobre ven.ventas(fecha_local) para evitar Seq Scan en
-- listados por día/rango (PASO 3, BACK-REND-018 / DB-RND-007).
-- ven.ventas ya tiene fecha_local (DATE GENERATED), pero faltaban índices.
-- Idempotente: IF NOT EXISTS. En prod usar CONCURRENTLY si hay carga.
-- También integrado en scripts/02_tablas.sql para installs nuevos.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_ventas_fecha_local
    ON ven.ventas(fecha_local DESC);

CREATE INDEX IF NOT EXISTS idx_ventas_almacen_fecha_local
    ON ven.ventas(almacen_id, fecha_local DESC);
