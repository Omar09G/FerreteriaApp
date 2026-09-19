-- ============================================================================
-- V16__devoluciones_estado_y_total.sql
-- Devoluciones completas: nuevos estados de venta + total recalculado.
-- 1. Guarda y dropea TODA vista (ven/fin/inv) que toque ven.ventas+estado,
--    ensancha estado a VARCHAR(16), recrea las vistas idénticas, y repone
--    el CHECK con DEVUELTA_PARCIAL/DEVUELTA_TOTAL.
-- 2. Total de devoluciones_venta recalculado desde detalles (espejo de
--    fn_recalc_totales_venta) + backfills de totales y estados históricos.
-- ============================================================================

DO $$
DECLARE r RECORD;
BEGIN
    CREATE TEMP TABLE IF NOT EXISTS _v16_vistas(esquema TEXT, nombre TEXT, def TEXT);
    DELETE FROM _v16_vistas;
    FOR r IN
        SELECT n.nspname AS esquema, c.relname AS nombre, pg_get_viewdef(c.oid) AS def
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'v' AND n.nspname IN ('ven', 'fin', 'inv')
          AND pg_get_viewdef(c.oid) LIKE '%ventas%'
          AND pg_get_viewdef(c.oid) LIKE '%estado%'
    LOOP
        INSERT INTO _v16_vistas VALUES (r.esquema, r.nombre, r.def);
        EXECUTE format('DROP VIEW %I.%I', r.esquema, r.nombre);
    END LOOP;
END $$;

ALTER TABLE ven.ventas DROP CONSTRAINT IF EXISTS ventas_estado_check;
-- 'DEVUELTA_PARCIAL' mide 15: ensanchar antes del nuevo CHECK.
ALTER TABLE ven.ventas ALTER COLUMN estado TYPE VARCHAR(16);
ALTER TABLE ven.ventas ADD CONSTRAINT ventas_estado_check
    CHECK (estado IN ('COMPLETADA','CANCELADA','DEVUELTA_PARCIAL','DEVUELTA_TOTAL'));

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT * FROM _v16_vistas LOOP
        EXECUTE format('CREATE OR REPLACE VIEW %I.%I AS %s', r.esquema, r.nombre, r.def);
    END LOOP;
    DROP TABLE _v16_vistas;
END $$;

CREATE OR REPLACE FUNCTION ven.fn_recalc_total_devolucion()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_did BIGINT := COALESCE(NEW.devolucion_id, OLD.devolucion_id);
BEGIN
    UPDATE ven.devoluciones_venta
       SET total = (SELECT COALESCE(SUM(importe_linea), 0)
                      FROM ven.devolucion_detalles WHERE devolucion_id = v_did)
     WHERE devolucion_id = v_did;
    RETURN COALESCE(NEW, OLD);
END $$;

DROP TRIGGER IF EXISTS trg_devolucion_totales ON ven.devolucion_detalles;
CREATE TRIGGER trg_devolucion_totales
AFTER INSERT OR DELETE ON ven.devolucion_detalles
FOR EACH ROW EXECUTE FUNCTION ven.fn_recalc_total_devolucion();

-- Backfill: devoluciones históricas con total en 0 (ej. DV-00000002).
UPDATE ven.devoluciones_venta d
   SET total = (SELECT COALESCE(SUM(importe_linea), 0)
                  FROM ven.devolucion_detalles WHERE devolucion_id = d.devolucion_id)
 WHERE d.total = 0;

-- Backfill: ventas COMPLETADA con devoluciones -> estado por cobertura acumulada.
UPDATE ven.ventas v SET estado = 'DEVUELTA_TOTAL'
 WHERE v.estado = 'COMPLETADA'
   AND EXISTS (SELECT 1 FROM ven.devoluciones_venta d WHERE d.venta_id = v.venta_id)
   AND NOT EXISTS (
    SELECT 1 FROM ven.venta_detalles vd WHERE vd.venta_id = v.venta_id
      AND vd.cantidad > COALESCE((SELECT SUM(dd.cantidad) FROM ven.devolucion_detalles dd
        JOIN ven.devoluciones_venta d ON d.devolucion_id = dd.devolucion_id
        WHERE d.venta_id = v.venta_id AND dd.venta_detalle_id = vd.venta_detalle_id), 0));
UPDATE ven.ventas v SET estado = 'DEVUELTA_PARCIAL'
 WHERE v.estado = 'COMPLETADA'
   AND EXISTS (SELECT 1 FROM ven.devoluciones_venta d WHERE d.venta_id = v.venta_id);
