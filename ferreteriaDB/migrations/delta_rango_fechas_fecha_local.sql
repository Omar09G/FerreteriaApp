-- ============================================================================
-- DELTA: Filtros por rango de fechas (aplicar a BD existente)
-- Añade columna fecha_local (DATE GENERATED, TZ America/Mexico_City) a las
-- tablas con fecha TIMESTAMPTZ para filtrar por día sin desfase de zona,
-- mismmo patrón que ven.ventas.fecha_local.
-- También integrado en scripts/02_tablas.sql para installs nuevos.
-- ============================================================================

-- ------------------------------------------------------------------
-- 1. ven.cotizaciones (listado de cotizaciones por rango)
-- ------------------------------------------------------------------
ALTER TABLE ven.cotizaciones
    ADD COLUMN IF NOT EXISTS fecha_local DATE GENERATED ALWAYS AS
        ((fecha AT TIME ZONE 'America/Mexico_City')::date) STORED;
CREATE INDEX IF NOT EXISTS idx_cotizaciones_fecha_local
    ON ven.cotizaciones(fecha_local DESC);

-- ------------------------------------------------------------------
-- 2. ven.rentas (listado de rentas por rango)
-- ------------------------------------------------------------------
ALTER TABLE ven.rentas
    ADD COLUMN IF NOT EXISTS fecha_local DATE GENERATED ALWAYS AS
        ((fecha_renta AT TIME ZONE 'America/Mexico_City')::date) STORED;
CREATE INDEX IF NOT EXISTS idx_rentas_fecha_local
    ON ven.rentas(fecha_local DESC);

-- ------------------------------------------------------------------
-- 3. com.compras (listado de compras por rango)
-- ------------------------------------------------------------------
ALTER TABLE com.compras
    ADD COLUMN IF NOT EXISTS fecha_local DATE GENERATED ALWAYS AS
        ((fecha AT TIME ZONE 'America/Mexico_City')::date) STORED;
CREATE INDEX IF NOT EXISTS idx_compras_fecha_local
    ON com.compras(fecha_local DESC);

-- ------------------------------------------------------------------
-- 4. fin.ingresos_otros (listado de ingresos por rango) — índice para
--    findByFechaBetweenOrderByCreadoEnDesc
-- ------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ingresos_otros_fecha
    ON fin.ingresos_otros(fecha DESC);