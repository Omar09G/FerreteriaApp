-- ============================================================================
-- V8: Índices trigram para filtros ILIKE de auditoría (BACK-REND-009)
-- ============================================================================
-- Las columnas JSONB (datos_anteriores, datos_nuevos) y el username se filtran
-- con ILIKE en AuditoriaRepository.buscar(). Sin trigram, Postgres hace SeqScan.
-- Activamos pg_trgm y creamos GIN trigram en cada columna. Idempotente.
--
-- Justificación del modelo:
--  - GIN con gin_trgm_ops soporta ILIKE/LIKE con comodines, EXACTAMENTE el patrón
--    usado por el backend (`%texto%`). Tamaño ≈ 3× la columna, despreciable vs
--    315M filas/año esperadas.
--  - idx_auditoria_fecha (ya existente) cubre ORDER BY creado_en DESC; combined
--    con estos trigram por planner según selectividad.
--
-- Tablas afectadas:
--   - seg.usuarios  (filtro por username en JOIN)
--   - seg.auditoria (filtro por contenido de datos_anteriores/datos_nuevos)
--
-- Pre-condición: pg_trgm ya activado por V1 (02_tablas.sql línea 55).
-- ============================================================================

-- Username: usa el índice solo cuando el predicado es ILIKE/LIKE/~
-- (text_pattern_ops/gin_trgm_ops ya están cubiertos; añadimos gin_trgm_ops para
--  armonizar con el resto del esquema y soportar wildcards al inicio).
CREATE INDEX IF NOT EXISTS idx_usuarios_username_trgm
    ON seg.usuarios USING GIN (username gin_trgm_ops);

-- Datos JSONB → cast a text en la consulta. GIN sobre la expresión es válido y
-- persiste; alternativamente una expression index sobre la conversión.
CREATE INDEX IF NOT EXISTS idx_auditoria_datos_anteriores_trgm
    ON seg.auditoria USING GIN ((datos_anteriores::text) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_auditoria_datos_nuevos_trgm
    ON seg.auditoria USING GIN ((datos_nuevos::text) gin_trgm_ops);

-- Backfill opcional: ANALYZE para que el planner use estadísticas actualizadas.
ANALYZE seg.usuarios;
ANALYZE seg.auditoria;
