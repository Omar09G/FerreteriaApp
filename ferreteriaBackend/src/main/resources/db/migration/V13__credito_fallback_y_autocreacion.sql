-- ============================================================================
-- V13: Fallback de credito a ven.clientes.limiteCredito + backfill lineas
-- Si el cliente no tiene linea activa en ven.lineas_credito, usa su
-- limiteCredito/diasCredito como autorizacion implicita. Mantiene validacion
-- de disponible < total y sigue usando lineas cuando existen.
-- Tambien backfillea lineas para clientes existentes con limite>0 sin linea.
-- ============================================================================

CREATE OR REPLACE FUNCTION ven.fn_valida_credito(p_venta BIGINT, p_total NUMERIC)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_cli BIGINT; v_disp NUMERIC;
BEGIN
    SELECT cliente_id INTO v_cli FROM ven.ventas WHERE venta_id = p_venta;
    IF v_cli IS NULL THEN
        RAISE EXCEPTION 'Venta a credito requiere cliente identificado' USING ERRCODE = 'P0201';
    END IF;

    -- 1) Intento con linea activa explicita
    SELECT lc.monto_autorizado - COALESCE(SUM(cc.monto_total - cc.monto_pagado), 0)
      INTO v_disp
    FROM ven.lineas_credito lc
    LEFT JOIN ven.cuentas_cobrar cc
           ON cc.cliente_id = lc.cliente_id AND cc.estado IN ('VIGENTE','PARCIAL')
    WHERE lc.cliente_id = v_cli
      AND lc.estado = 'ACTIVA'
      AND (lc.vigente_hasta IS NULL OR lc.vigente_hasta >= CURRENT_DATE)
    GROUP BY lc.monto_autorizado;

    -- 2) Fallback a ven.clientes.limiteCredito si no hay linea activa
    IF v_disp IS NULL THEN
        SELECT c.limite_credito - COALESCE(SUM(cc2.monto_total - cc2.monto_pagado), 0)
          INTO v_disp
        FROM ven.clientes c
        LEFT JOIN ven.cuentas_cobrar cc2
               ON cc2.cliente_id = c.cliente_id AND cc2.estado IN ('VIGENTE','PARCIAL')
        WHERE c.cliente_id = v_cli
        GROUP BY c.limite_credito;

        IF v_disp IS NULL THEN
            RAISE EXCEPTION 'Cliente % sin linea de credito activa', v_cli USING ERRCODE = 'P0201';
        END IF;
        -- Si el cliente tiene limite 0, se considera sin credito
        IF v_disp <= 0 AND COALESCE((SELECT limite_credito FROM ven.clientes WHERE cliente_id = v_cli), 0) <= 0 THEN
            RAISE EXCEPTION 'Cliente % sin linea de credito activa', v_cli USING ERRCODE = 'P0201';
        END IF;
    END IF;

    IF v_disp < p_total THEN
        RAISE EXCEPTION 'Credito insuficiente para cliente %: disponible %, venta %',
            v_cli, v_disp, p_total USING ERRCODE = 'P0200';
    END IF;
END $$;

ALTER FUNCTION ven.fn_valida_credito(BIGINT, NUMERIC) SET search_path = pg_catalog, public, ven;

-- Backfill: crea linea activa para clientes existentes con limite>0 que no tienen linea activa
INSERT INTO ven.lineas_credito (cliente_id, monto_autorizado, dias_credito, tasa_moratorio, usuario_autorizo_id, estado, observaciones)
SELECT c.cliente_id,
       c.limite_credito,
       GREATEST(c.dias_credito, 7),
       0,
       1,
       'ACTIVA',
       'Auto-creada por V13 fallback (limite del cliente)'
FROM ven.clientes c
WHERE c.limite_credito > 0
  AND c.activo
  AND NOT EXISTS (
    SELECT 1 FROM ven.lineas_credito lc
    WHERE lc.cliente_id = c.cliente_id AND lc.estado = 'ACTIVA'
      AND (lc.vigente_hasta IS NULL OR lc.vigente_hasta >= CURRENT_DATE)
  )
ON CONFLICT DO NOTHING;
