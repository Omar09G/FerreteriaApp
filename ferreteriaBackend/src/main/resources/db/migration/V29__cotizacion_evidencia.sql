-- ============================================================================
-- V29__cotizacion_evidencia.sql
-- Cotizador con foto: evidencia (foto del cliente) en ven.cotizaciones,
-- misma regla que foto_url/imagen_url (https:// o data:image/).
-- Idempotente. Espejo: scripts/02_tablas.sql y
-- migrations/delta_cotizacion_evidencia.sql.
-- ============================================================================

ALTER TABLE ven.cotizaciones
    ADD COLUMN IF NOT EXISTS evidencia_url TEXT
    CHECK (evidencia_url IS NULL OR evidencia_url ~ '^https?://|^data:image/');
