-- ============================================================================
-- DELTA: motivo de cancelación de ventas
-- Agrega motivo_cancelacion TEXT a ven.ventas (NULL = no cancelada).
-- Idempotente: ADD COLUMN IF NOT EXISTS.
-- También integrado en scripts/02_tablas.sql y migración V20 del backend.
-- ============================================================================

ALTER TABLE ven.ventas ADD COLUMN IF NOT EXISTS motivo_cancelacion TEXT;
