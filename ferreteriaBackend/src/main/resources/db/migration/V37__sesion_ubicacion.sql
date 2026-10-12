-- V37: ubicación aproximada del login en seg.sesiones (GeoIP local).
-- Solo texto ciudad/región/país, nunca coordenadas. NULL = no resuelta
-- (IP privada, sin base GeoIP o lookup fallido). Idempotente.
-- En installs nuevos ya viene en V1__base regenerado desde scripts/02_tablas.sql.

ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS ciudad VARCHAR(120);
ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS region VARCHAR(120);
ALTER TABLE seg.sesiones ADD COLUMN IF NOT EXISTS pais   VARCHAR(120);
