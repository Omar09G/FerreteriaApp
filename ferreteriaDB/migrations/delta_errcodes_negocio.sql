-- ============================================================================
-- DELTA V3: ERRCODE propios (clase P0, definidos por la app) en reglas de negocio
-- Contrato handler Java: P0100 STOCK_INSUFICIENTE | P0200 CREDITO_EXCEDIDO
--   P0201 CREDITO_NO_DISPONIBLE | P0300 TURNO_YA_CERRADO | P0301 TURNO_NO_ENCONTRADO
--   P0302 VALOR_INVALIDO(monto) | P0400 PROMOCION_AGOTADA | P0401 PROMOCION_LIMITE_CLIENTE
--   P0999 KARDEX_APPEND_ONLY
-- Idempotente (CREATE OR REPLACE). Aplicar tras 02_tablas.sql.
-- ============================================================================

CREATE OR REPLACE FUNCTION fin.fn_cerrar_turno(p_turno bigint, p_monto_contado numeric, p_usuario_cierre integer DEFAULT NULL::integer, p_notas text DEFAULT NULL::text)
 RETURNS bigint
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_estado TEXT; v_caja INT; v_usr INT; v_alm INT;
    v_apertura NUMERIC; v_apertura_en TIMESTAMPTZ;
    v_num INTEGER; v_sub NUMERIC; v_iva NUMERIC; v_desc NUMERIC;
    v_tot NUMERIC; v_costo NUMERIC;
    v_ent_eff NUMERIC; v_sal_eff NUMERIC; v_ent_dig NUMERIC; v_sal_dig NUMERIC;
    v_desg_e JSONB; v_desg_s JSONB; v_desg_fp JSONB;
    v_perdidas NUMERIC; v_esperado NUMERIC; v_id BIGINT;
BEGIN
    SELECT estado, caja_id, usuario_id, monto_apertura, apertura_en
      INTO v_estado, v_caja, v_usr, v_apertura, v_apertura_en
    FROM fin.turnos_caja WHERE turno_caja_id = p_turno FOR UPDATE;

    IF v_estado IS NULL THEN
        RAISE EXCEPTION 'Turno % no existe', p_turno
        USING ERRCODE = 'P0301';
    END IF;
    IF v_estado <> 'ABIERTO' THEN
        RAISE EXCEPTION 'El turno % ya está cerrado', p_turno
        USING ERRCODE = 'P0300';
    END IF;
    IF p_monto_contado IS NULL OR p_monto_contado < 0 THEN
        RAISE EXCEPTION 'El monto contado es obligatorio y no puede ser negativo'
        USING ERRCODE = 'P0302';
    END IF;

    SELECT almacen_id INTO v_alm FROM fin.cajas WHERE caja_id = v_caja;

    -- ===== Ventas del turno =====
    SELECT COUNT(*),
           COALESCE(SUM(v.subtotal),0), COALESCE(SUM(v.iva),0),
           COALESCE(SUM(v.descuento_total),0), COALESCE(SUM(v.total),0)
      INTO v_num, v_sub, v_iva, v_desc, v_tot
    FROM ven.ventas v
    WHERE v.turno_caja_id = p_turno AND v.estado = 'COMPLETADA';

    SELECT COALESCE(SUM(d.cantidad * d.costo_unitario), 0) INTO v_costo
    FROM ven.venta_detalles d
    JOIN ven.ventas v ON v.venta_id = d.venta_id
    WHERE v.turno_caja_id = p_turno AND v.estado = 'COMPLETADA';

    -- ===== Movimientos del turno (entradas/salidas, efectivo vs digital) =====
    WITH mv AS (
        SELECT mc.tipo, mc.concepto, mc.monto,
               COALESCE(fp.es_efectivo, true) AS es_eff,
               COALESCE(fp.nombre, 'EFECTIVO') AS forma
        FROM fin.movimientos_caja mc
        LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
        WHERE mc.turno_caja_id = p_turno
          AND mc.concepto <> 'APERTURA'
    )
    SELECT
        COALESCE(SUM(monto) FILTER (WHERE tipo='ENTRADA' AND es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='SALIDA'  AND es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='ENTRADA' AND NOT es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='SALIDA'  AND NOT es_eff), 0),
        (SELECT COALESCE(jsonb_object_agg(concepto, m ORDER BY concepto), '{}'::jsonb)
           FROM (SELECT concepto, SUM(monto) m FROM mv WHERE tipo='ENTRADA' GROUP BY concepto) x),
        (SELECT COALESCE(jsonb_object_agg(concepto, m ORDER BY concepto), '{}'::jsonb)
           FROM (SELECT concepto, SUM(monto) m FROM mv WHERE tipo='SALIDA' GROUP BY concepto) x),
        (SELECT COALESCE(jsonb_object_agg(forma, m ORDER BY forma), '{}'::jsonb)
           FROM (SELECT forma, SUM(monto) m FROM mv GROUP BY forma) x)
    INTO v_ent_eff, v_sal_eff, v_ent_dig, v_sal_dig, v_desg_e, v_desg_s, v_desg_fp
    FROM mv;

    -- ===== Pérdidas de inventario del turno (a costo) =====
    SELECT COALESCE(SUM(m.cantidad * COALESCE(m.costo_unitario, p.costo_actual)), 0)
      INTO v_perdidas
    FROM inv.movimientos_inventario m
    JOIN inv.productos p ON p.producto_id = m.producto_id
    JOIN cat.motivos_movimiento mo ON mo.motivo_id = m.motivo_id
    WHERE m.almacen_id = v_alm
      AND m.tipo = 'SALIDA'
      AND mo.clave IN ('DETERIORO','USO_INTERNO','MUESTRA')
      AND m.creado_en BETWEEN v_apertura_en AND now();

    -- ===== Cerrar el turno =====
    v_esperado := v_apertura + v_ent_eff - v_sal_eff;

    UPDATE fin.turnos_caja
       SET estado         = 'CERRADO',
           cierre_en      = now(),
           monto_esperado = v_esperado,
           monto_contado  = p_monto_contado,
           diferencia     = p_monto_contado - v_esperado,
           observaciones  = COALESCE(NULLIF(p_notas,''), observaciones)
     WHERE turno_caja_id = p_turno;

    -- ===== Histórico inmutable =====
    INSERT INTO fin.cortes_caja
        (turno_caja_id, caja_id, almacen_id, usuario_id, usuario_cierre_id,
         apertura_en, num_ventas, subtotal, iva, descuentos, total_vendido,
         costo_ventas, fondo_apertura, entradas_efectivo, salidas_efectivo,
         dinero_esperado, dinero_contado, diferencia,
         ingresos_no_efectivo, egresos_no_efectivo, perdidas_inventario,
         desglose_entradas, desglose_salidas, desglose_formas_pago, observaciones)
    VALUES
        (p_turno, v_caja, v_alm, v_usr,
         COALESCE(p_usuario_cierre, v_usr),
         v_apertura_en, v_num, v_sub, v_iva, v_desc, v_tot,
         v_costo, v_apertura, v_ent_eff, v_sal_eff,
         v_esperado, p_monto_contado, p_monto_contado - v_esperado,
         v_ent_dig, v_sal_dig, v_perdidas,
         v_desg_e, v_desg_s, v_desg_fp, p_notas)
    RETURNING corte_id INTO v_id;

    RETURN v_id;
END $function$

;

CREATE OR REPLACE FUNCTION fin.fn_movimiento_caja(p_turno bigint, p_tipo text, p_concepto text, p_monto numeric, p_forma_pago integer DEFAULT NULL::integer, p_ref_tabla text DEFAULT NULL::text, p_ref_id bigint DEFAULT NULL::bigint, p_usuario integer DEFAULT NULL::integer)
 RETURNS bigint
 LANGUAGE plpgsql
AS $function$
DECLARE v_id BIGINT; v_efectivo BOOLEAN := TRUE; v_abierto TEXT;
BEGIN
    IF p_forma_pago IS NOT NULL THEN
        SELECT es_efectivo INTO v_efectivo
        FROM cat.formas_pago WHERE forma_pago_id = p_forma_pago;
    END IF;

    SELECT estado INTO v_abierto FROM fin.turnos_caja WHERE turno_caja_id = p_turno;
    IF v_abierto IS DISTINCT FROM 'ABIERTO' THEN
        RAISE EXCEPTION 'El turno % no está abierto', p_turno
        USING ERRCODE = 'P0300';
    END IF;

    INSERT INTO fin.movimientos_caja
        (turno_caja_id, tipo, concepto, monto, forma_pago_id, ref_tabla, ref_id, usuario_id)
    VALUES
        (p_turno, p_tipo, p_concepto, p_monto, p_forma_pago, p_ref_tabla, p_ref_id, p_usuario)
    RETURNING movimiento_id INTO v_id;
    RETURN v_id;
END $function$

;

CREATE OR REPLACE FUNCTION inv.fn_aplica_movimiento_stock()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE v_delta NUMERIC := CASE WHEN NEW.tipo = 'ENTRADA' THEN NEW.cantidad
                                                 ELSE -NEW.cantidad END;
        v_nuevo NUMERIC;
BEGIN
    SELECT COALESCE(i.stock, 0) + v_delta INTO v_nuevo
    FROM inv.inventario i
    WHERE i.producto_id = NEW.producto_id AND i.almacen_id = NEW.almacen_id;

    IF v_nuevo < 0 THEN
        IF COALESCE((SELECT valor::boolean FROM cfg.configuracion
                     WHERE clave = 'permitir_stock_negativo'), false) = false THEN
            RAISE EXCEPTION 'Stock negativo no permitido: producto % quedaria en %',
                NEW.producto_id, v_nuevo
        USING ERRCODE = 'P0100';
        END IF;
    END IF;

    INSERT INTO inv.inventario (producto_id, almacen_id, stock)
    VALUES (NEW.producto_id, NEW.almacen_id, v_delta)
    ON CONFLICT (producto_id, almacen_id)
    DO UPDATE SET stock = inv.inventario.stock + EXCLUDED.stock,
                  actualizado_en = now();
    RETURN NEW;
END $function$

;

CREATE OR REPLACE FUNCTION inv.fn_kardex_solo_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN RAISE EXCEPTION 'movimientos_inventario es append-only'
        USING ERRCODE = 'P0999'; END $function$

;

CREATE OR REPLACE FUNCTION ven.fn_detalle_valida_stock()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE v_tipo TEXT; v_disp NUMERIC; v_permitir_neg BOOLEAN;
BEGIN
    SELECT tipo INTO v_tipo FROM inv.productos WHERE producto_id = NEW.producto_id;
    IF v_tipo = 'PRODUCTO' THEN
        SELECT i.stock - i.reservado INTO v_disp
        FROM inv.inventario i
        JOIN ven.ventas v ON v.almacen_id = i.almacen_id AND v.venta_id = NEW.venta_id
        WHERE i.producto_id = NEW.producto_id;

        IF v_disp IS NULL OR v_disp < NEW.cantidad THEN
            SELECT COALESCE(valor::boolean, false) INTO v_permitir_neg
            FROM cfg.configuracion WHERE clave = 'permitir_stock_negativo';
            IF COALESCE(v_permitir_neg, false) = false THEN
                RAISE EXCEPTION 'Stock insuficiente producto % disponible % solicitado %',
                    NEW.producto_id, COALESCE(v_disp, 0), NEW.cantidad
        USING ERRCODE = 'P0100';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $function$

;

CREATE OR REPLACE FUNCTION ven.fn_registrar_uso_promo(p_promocion bigint, p_venta bigint, p_cliente bigint, p_descuento numeric, p_usuario integer)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE v_max_total INT; v_usados INT; v_max_cli INT; v_usos_cli INT;
BEGIN
    SELECT max_usos_total, usos_actual, max_usos_cliente
      INTO v_max_total, v_usados, v_max_cli
    FROM ven.promociones WHERE promocion_id = p_promocion FOR UPDATE;

    IF v_max_total IS NOT NULL AND v_usados >= v_max_total THEN
        RAISE EXCEPTION 'Promocion % agotada', p_promocion
        USING ERRCODE = 'P0400';
    END IF;

    IF v_max_cli IS NOT NULL AND p_cliente IS NOT NULL THEN
        SELECT COUNT(*) INTO v_usos_cli
        FROM ven.promocion_usos
        WHERE promocion_id = p_promocion AND cliente_id = p_cliente;
        IF v_usos_cli >= v_max_cli THEN
            RAISE EXCEPTION 'El cliente alcanzo el limite de usos de esta promocion'
        USING ERRCODE = 'P0401';
        END IF;
    END IF;

    INSERT INTO ven.promocion_usos
        (promocion_id, venta_id, cliente_id, monto_descuento, usuario_id)
    VALUES (p_promocion, p_venta, p_cliente, p_descuento, p_usuario);

    UPDATE ven.promociones SET usos_actual = usos_actual + 1
    WHERE promocion_id = p_promocion;
END $function$

;

CREATE OR REPLACE FUNCTION ven.fn_valida_credito(p_venta bigint, p_total numeric)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
DECLARE v_cli BIGINT; v_disp NUMERIC;
BEGIN
    SELECT cliente_id INTO v_cli FROM ven.ventas WHERE venta_id = p_venta;
    IF v_cli IS NULL THEN
        RAISE EXCEPTION 'Venta a credito requiere cliente identificado'
        USING ERRCODE = 'P0201';
    END IF;

    SELECT lc.monto_autorizado - COALESCE(SUM(cc.monto_total - cc.monto_pagado), 0)
      INTO v_disp
    FROM ven.lineas_credito lc
    LEFT JOIN ven.cuentas_cobrar cc
           ON cc.cliente_id = lc.cliente_id AND cc.estado IN ('VIGENTE','PARCIAL')
    WHERE lc.cliente_id = v_cli
      AND lc.estado = 'ACTIVA'
      AND (lc.vigente_hasta IS NULL OR lc.vigente_hasta >= CURRENT_DATE)
    GROUP BY lc.monto_autorizado;

    IF v_disp IS NULL THEN
        RAISE EXCEPTION 'Cliente % sin linea de credito activa', v_cli
        USING ERRCODE = 'P0201';
    END IF;
    IF v_disp < p_total THEN
        RAISE EXCEPTION 'Credito insuficiente para cliente %: disponible %, venta %',
            v_cli, v_disp, p_total
        USING ERRCODE = 'P0200';
    END IF;
END $function$

;

