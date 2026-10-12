-- DELTA: ubicación aproximada del login en seg.sesiones (GeoIP local).
-- Espejo de V37__sesion_ubicacion.sql (Flyway, backend). Idempotente.
-- Solo texto ciudad/región/país, nunca coordenadas. NULL = no resuelta
-- (IP privada, sin base GeoIP o lookup fallido).

ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS ciudad VARCHAR(120);
ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS region VARCHAR(120);
ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS pais   VARCHAR(120);
