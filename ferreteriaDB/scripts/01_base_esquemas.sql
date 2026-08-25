-- ============================================================================
-- 01_base_esquemas.sql
-- Sistema Integral de Ferretería · Paso 1/5
-- Base de datos, zona horaria México, rol de aplicación, esquemas y extensiones
--
-- Ejecutar como superusuario:
--   psql -U postgres -f 01_base_esquemas.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Base de datos (idempotente)
-- ----------------------------------------------------------------------------
SELECT format(
    'CREATE DATABASE ferreteria WITH ENCODING %L TEMPLATE template0',
    'UTF8')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ferreteria')\gexec

\connect ferreteria

-- Hora local México a nivel base de datos (REGLA GLOBAL del sistema)
ALTER DATABASE ferreteria SET timezone TO 'America/Mexico_City';

-- ----------------------------------------------------------------------------
-- 2. Rol de aplicación
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ferreteria_app') THEN
        CREATE ROLE ferreteria_app LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION';
        RAISE NOTICE 'Rol ferreteria_app creado (cambiar password en produccion)';
    ELSE
        RAISE NOTICE 'Rol ferreteria_app ya existe';
    END IF;
END $$;

ALTER ROLE ferreteria_app SET timezone TO 'America/Mexico_City';
ALTER ROLE ferreteria_app SET client_encoding TO 'UTF8';

-- ----------------------------------------------------------------------------
-- 3. Esquemas por módulo
-- ----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS cat;   -- catálogos
CREATE SCHEMA IF NOT EXISTS cfg;   -- configuración y folios
CREATE SCHEMA IF NOT EXISTS rh;    -- recursos humanos
CREATE SCHEMA IF NOT EXISTS seg;   -- seguridad y auditoría
CREATE SCHEMA IF NOT EXISTS inv;   -- inventario y productos
CREATE SCHEMA IF NOT EXISTS com;   -- compras
CREATE SCHEMA IF NOT EXISTS ven;   -- ventas
CREATE SCHEMA IF NOT EXISTS fin;   -- finanzas / caja
CREATE SCHEMA IF NOT EXISTS fis;   -- fiscal (catálogos SAT e impuestos)

-- ----------------------------------------------------------------------------
-- 4. Extensiones
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- crypt()/gen_salt() para hashes bcrypt
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- búsqueda parcial de nombres (POS)

-- ----------------------------------------------------------------------------
-- 5. Acceso básico al rol de aplicación (GRANTs finos al final de 02_tablas.sql)
-- ----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis TO ferreteria_app;

SELECT 'PASO 1 COMPLETO: base, rol y esquemas listos.' AS resultado;
