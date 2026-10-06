-- ============================================================================
-- delta_cotizacion_evidencia.sql
-- Espejo de V29__cotizacion_evidencia.sql del backend (fuente:
-- scripts/02_tablas.sql). Evidencia fotográfica en ven.cotizaciones.
-- Idempotente.
-- ============================================================================

ALTER TABLE ven.cotizaciones
    ADD COLUMN IF NOT EXISTS evidencia_url TEXT
    CHECK (evidencia_url IS NULL OR evidencia_url ~ '^https?://|^data:image/');
