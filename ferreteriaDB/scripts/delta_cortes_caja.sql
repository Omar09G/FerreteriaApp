-- ============================================================================
-- DELTA: Corte de caja histórico (aplicar a BD existente)
-- También integrado en scripts/02_tablas.sql y vistas_core.sql para installs nuevos
-- ============================================================================

-- ------------------------------------------------------------------
-- 1. Tabla histórica: una fila INMUTABLE por cada corte de caja cerrado
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fin.cortes_caja (
    corte_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turno_caja_id       BIGINT NOT NULL UNIQUE REFERENCES fin.turnos_caja(turno_caja_id),
    caja_id             INTEGER NOT NULL REFERENCES fin.cajas(caja_id),
    almacen_id          INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    usuario_id          INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),   -- cajero del turno
    usuario_cierre_id   INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),   -- quien hizo el corte
    fecha               DATE NOT NULL DEFAULT CURRENT_DATE,
    apertura_en         TIMESTAMPTZ NOT NULL,
    cierre_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ---- Operación vendida en el turno ----
    num_ventas          INTEGER  NOT NULL DEFAULT 0,
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuentos          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_vendido       NUMERIC(14,2) NOT NULL DEFAULT 0,
    costo_ventas        NUMERIC(14,2) NOT NULL DEFAULT 0,
    utilidad_bruta      NUMERIC(14,2) GENERATED ALWAYS AS (subtotal - costo_ventas) STORED,
    margen_pct          NUMERIC(6,2)  GENERATED ALWAYS AS
                        ((subtotal - costo_ventas) / NULLIF(subtotal,0) * 100) STORED,
    -- ---- Efectivo ----
    fondo_apertura      NUMERIC(14,2) NOT NULL DEFAULT 0,
    entradas_efectivo   NUMERIC(14,2) NOT NULL DEFAULT 0,
    salidas_efectivo    NUMERIC(14,2) NOT NULL DEFAULT 0,
    dinero_esperado     NUMERIC(14,2) NOT NULL DEFAULT 0,
    dinero_contado      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (dinero_contado >= 0),
    diferencia          NUMERIC(14,2) NOT NULL DEFAULT 0,
    -- ---- Flujos no efectivo y pérdidas de inventario a costo ----
    ingresos_no_efectivo NUMERIC(14,2) NOT NULL DEFAULT 0,
    egresos_no_efectivo  NUMERIC(14,2) NOT NULL DEFAULT 0,
    perdidas_inventario  NUMERIC(14,2) NOT NULL DEFAULT 0,   -- deterioro/uso interno/muestras
    -- ---- Desgloses congelados al momento del corte ----
    desglose_entradas    JSONB NOT NULL DEFAULT '{}'::jsonb,  -- {"COBRANZA_CREDITO":5000,...}
    desglose_salidas     JSONB NOT NULL DEFAULT '{}'::jsonb,
    desglose_formas_pago JSONB NOT NULL DEFAULT '{}'::jsonb,
    observaciones        TEXT,
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_corte_diferencia CHECK (dinero_contado >= 0)
);

CREATE INDEX IF NOT EXISTS idx_cortes_fecha ON fin.cortes_caja(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_cortes_caja  ON fin.cortes_caja(caja_id, fecha DESC);

COMMENT ON TABLE fin.cortes_caja IS
'Historial inmutable de cortes de caja: congela ventas, utilidad, margen, efectivo
esperado/contado/diferencia, pérdidas de inventario y desgloses de flujo del turno.';

-- ------------------------------------------------------------------
-- 2. Función de cierre: valida, calcula TODO, cierra turno e inserta histórico
-- ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fin.fn_cerrar_turno(
    p_turno BIGINT,
    p_monto_contado NUMERIC,
    p_usuario_cierre INT DEFAULT NULL,   -- si NULL usa el cajero del turno
    p_notas TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
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
        RAISE EXCEPTION 'Turno % no existe', p_turno;
    END IF;
    IF v_estado <> 'ABIERTO' THEN
        RAISE EXCEPTION 'El turno % ya está cerrado', p_turno;
    END IF;
    IF p_monto_contado IS NULL OR p_monto_contado < 0 THEN
        RAISE EXCEPTION 'El monto contado es obligatorio y no puede ser negativo';
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
END $$;

COMMENT ON FUNCTION fin.fn_cerrar_turno IS
'Corte de caja transaccional: calcula ventas/utilidad/margen/efectivo esperado,
cierra el turno y congela todo en fin.cortes_caja (histórico). Idempotente por diseño
(falla si el turno ya está cerrado).';
