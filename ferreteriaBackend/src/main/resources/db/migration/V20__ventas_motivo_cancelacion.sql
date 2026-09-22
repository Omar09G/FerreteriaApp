-- ============================================================================
-- V20__ventas_motivo_cancelacion.sql
-- Persiste el motivo informado en PATCH /ventas/{id}/cancelar
-- (VentaCancelRequest.motivo, @NotBlank). NULL = venta no cancelada.
-- Espejo en ferreteriaDB: scripts/02_tablas.sql (definición) y
-- migrations/delta_ventas_motivo_cancelacion.sql (delta idempotente).
-- Idempotente: ADD COLUMN IF NOT EXISTS (propaga a particiones).
-- ============================================================================

ALTER TABLE ven.ventas ADD COLUMN IF NOT EXISTS motivo_cancelacion TEXT;
