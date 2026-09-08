-- V4: Corrige fn_recalc_totales_compra para compras multi-detalle CONTADO.
-- Bug: trigger FOR EACH ROW generaba LIQUIDADA en el primer detalle (890) y
-- bloqueaba UPDATE de monto_total al insertar el segundo detalle, dejando
-- monto_total=890 pero insertando pago delta 55 -> monto_pagado=945 violaba
-- chk_cp_pago_monto (monto_pagado <= monto_total).
-- Fix: al igual que ven.fn_recalc_totales_venta, permitir sincronizar
-- monto_total aun estando LIQUIDADA (solo bloquear CANCELADA) y clamptear
-- monto_pagado para no violar constraint cuando v_tot decrece. Sincronizar
-- estado/monto_pagado tras recortar pagos CONTADO (DELETE no dispara trigger).
CREATE OR REPLACE FUNCTION com.fn_recalc_totales_compra()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_cid BIGINT := COALESCE(NEW.compra_id, OLD.compra_id);
        v_tot NUMERIC; v_fp TEXT; v_cp BIGINT; v_prev NUMERIC;
        v_pagado NUMERIC; v_pid BIGINT; v_pmonto NUMERIC; v_exceso NUMERIC; v_dias SMALLINT;
BEGIN
    SELECT COALESCE(SUM(importe_linea), 0) INTO v_tot
    FROM com.compra_detalles WHERE compra_id = v_cid;

    UPDATE com.compras SET total = v_tot, subtotal = v_tot WHERE compra_id = v_cid;

    IF v_tot > 0 THEN
        SELECT fp.clave INTO v_fp
        FROM com.compras c JOIN cat.formas_pago fp ON fp.forma_pago_id = c.forma_pago_id
        WHERE c.compra_id = v_cid;

        SELECT cuenta_pagar_id, monto_total INTO v_cp, v_prev
        FROM com.cuentas_pagar WHERE compra_id = v_cid FOR UPDATE;

        IF NOT FOUND THEN
            SELECT COALESCE(pv.dias_credito, 0) INTO v_dias
            FROM com.compras c JOIN com.proveedores pv ON pv.proveedor_id = c.proveedor_id
            WHERE c.compra_id = v_cid;

            INSERT INTO com.cuentas_pagar (compra_id, monto_total, fecha_vencimiento)
            VALUES (v_cid, v_tot, CURRENT_DATE + v_dias)
            RETURNING cuenta_pagar_id INTO v_cp;
        ELSIF v_prev <> v_tot THEN
            -- Al llegar aqui el trigger pudo ejecutarse varias veces (detalles
            -- insertados uno por uno desde la app). Una cuenta de CONTADO puede
            -- ya estar LIQUIDADA por el pago auto generado en la primera linea;
            -- aun asi hay que sincronizar monto_total con v_tot y dejar que el
            -- bloque CONCILIA_PAGOS ajuste los pagos CONTADO. Se clamptea
            -- monto_pagado para no violar chk_cp_pago_monto cuando v_tot decrece.
            UPDATE com.cuentas_pagar
               SET monto_total = v_tot,
                   monto_pagado = LEAST(monto_pagado, v_tot),
                   estado = CASE
                       WHEN LEAST(monto_pagado, v_tot) >= v_tot THEN 'LIQUIDADA'
                       WHEN LEAST(monto_pagado, v_tot) > 0 THEN 'PARCIAL'
                       ELSE 'VIGENTE' END
             WHERE cuenta_pagar_id = v_cp AND estado <> 'CANCELADA';
        END IF;

        IF v_fp <> 'CREDITO' THEN
            SELECT COALESCE(SUM(monto), 0) INTO v_pagado
            FROM com.pagos_proveedor
            WHERE cuenta_pagar_id = v_cp AND referencia = 'CONTADO';

            IF v_pagado < v_tot THEN
                INSERT INTO com.pagos_proveedor
                    (cuenta_pagar_id, forma_pago_id, monto, usuario_id, turno_caja_id, referencia)
                SELECT v_cp, forma_pago_id, v_tot - v_pagado, usuario_id, turno_caja_id, 'CONTADO'
                FROM com.compras WHERE compra_id = v_cid;
            ELSIF v_pagado > v_tot THEN
                v_exceso := v_pagado - v_tot;
                FOR v_pid, v_pmonto IN SELECT pago_proveedor_id, monto
                                       FROM com.pagos_proveedor
                                       WHERE cuenta_pagar_id = v_cp AND referencia = 'CONTADO'
                                       ORDER BY pago_proveedor_id DESC FOR UPDATE
                LOOP
                    EXIT WHEN v_exceso <= 0;
                    IF v_pmonto <= v_exceso THEN
                        DELETE FROM com.pagos_proveedor WHERE pago_proveedor_id = v_pid;
                        v_exceso := v_exceso - v_pmonto;
                    ELSE
                        UPDATE com.pagos_proveedor SET monto = v_pmonto - v_exceso
                        WHERE pago_proveedor_id = v_pid;
                        v_exceso := 0;
                    END IF;
                END LOOP;
                -- Sincronizar monto_pagado/estado tras recortar pagos CONTADO
                -- (DELETE/UPDATE de pagos no dispara trg_pago_proveedor_post).
                SELECT COALESCE(SUM(monto), 0) INTO v_pagado
                FROM com.pagos_proveedor WHERE cuenta_pagar_id = v_cp;
                UPDATE com.cuentas_pagar
                   SET monto_pagado = v_pagado,
                       estado = CASE
                           WHEN v_pagado >= v_tot THEN 'LIQUIDADA'
                           WHEN v_pagado > 0 THEN 'PARCIAL'
                           ELSE 'VIGENTE' END
                 WHERE cuenta_pagar_id = v_cp;
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END $$;
