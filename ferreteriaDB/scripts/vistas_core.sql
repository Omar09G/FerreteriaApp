-- ============================================================================
-- vistas_core.sql (incluido desde 02_tablas.sql mediante \ir)
-- Las 21 vistas de negocio del sistema.
-- ============================================================================

SET timezone TO 'America/Mexico_City';

-- ===== §16.1 Top productos =====
CREATE OR REPLACE VIEW ven.vw_top_productos AS
SELECT date_trunc('month', v.fecha)::date                     AS mes,
       p.producto_id,
       p.codigo,
       p.nombre                                               AS producto,
       c.nombre                                               AS categoria,
       SUM(d.cantidad)::numeric(14,2)                         AS unidades_vendidas,
       SUM(d.total_linea)::numeric(14,2)                      AS ingreso_total,
       SUM(d.cantidad * d.costo_unitario)::numeric(14,2)      AS costo_total,
       (SUM(d.total_linea) - SUM(d.cantidad * d.costo_unitario))::numeric(14,2) AS utilidad,
       RANK() OVER (PARTITION BY date_trunc('month', v.fecha)
                    ORDER BY SUM(d.total_linea) DESC)         AS ranking_mes,
       RANK() OVER (PARTITION BY date_trunc('month', v.fecha)
                    ORDER BY SUM(d.cantidad) DESC)            AS ranking_unidades
FROM ven.venta_detalles d
JOIN ven.ventas v    ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
JOIN inv.productos p ON p.producto_id = d.producto_id
LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
GROUP BY date_trunc('month', v.fecha), p.producto_id, p.codigo, p.nombre, c.nombre;

-- ===== §16.2 Mejores clientes =====
CREATE OR REPLACE VIEW ven.vw_mejores_clientes AS
SELECT date_trunc('month', v.fecha)::date                     AS mes,
       cl.cliente_id,
       cl.razon_social                                        AS cliente,
       COUNT(DISTINCT v.venta_id)                             AS num_compras,
       SUM(v.total)::numeric(14,2)                            AS total_comprado,
       ROUND(AVG(v.total), 2)                                 AS ticket_promedio,
       RANK() OVER (PARTITION BY date_trunc('month', v.fecha)
                    ORDER BY SUM(v.total) DESC)               AS ranking_mes,
       RANK() OVER (ORDER BY SUM(v.total) DESC)               AS ranking_historico
FROM ven.ventas v
JOIN ven.clientes cl ON cl.cliente_id = v.cliente_id
WHERE v.estado = 'COMPLETADA'
GROUP BY date_trunc('month', v.fecha), cl.cliente_id, cl.razon_social;

-- ===== §16.3 Stock bajo =====
CREATE OR REPLACE VIEW inv.vw_stock_bajo AS
SELECT a.almacen_id,
       a.nombre                    AS almacen,
       p.producto_id,
       p.codigo,
       p.nombre                    AS producto,
       c.nombre                    AS categoria,
       i.stock,
       i.stock_minimo,
       GREATEST(COALESCE(i.stock_maximo, i.stock_minimo * 2) - i.stock, 0)::numeric(12,3)
                                   AS cantidad_sugerida_comprar,
       pr.razon_social             AS proveedor_principal,
       CASE WHEN i.stock <= 0 THEN 'AGOTADO' ELSE 'BAJO' END AS alerta
FROM inv.inventario i
JOIN inv.productos p ON p.producto_id = i.producto_id AND p.tipo = 'PRODUCTO' AND p.activo
JOIN inv.almacenes a ON a.almacen_id = i.almacen_id
LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
LEFT JOIN LATERAL (
    SELECT pv.razon_social
    FROM inv.producto_proveedores pp
    JOIN com.proveedores pv ON pv.proveedor_id = pp.proveedor_id
    WHERE pp.producto_id = p.producto_id
    ORDER BY pp.es_principal DESC NULLS LAST
    LIMIT 1
) pr ON true
WHERE i.stock <= i.stock_minimo
ORDER BY alerta DESC, i.stock ASC;

-- ===== §16.4 Ventas totales (diario, devengado) =====
CREATE OR REPLACE VIEW ven.vw_ventas_totales AS
WITH detalle_costo AS (
    SELECT d.venta_id,
           SUM(d.cantidad * d.costo_unitario) AS costo_ventas
    FROM ven.venta_detalles d
    GROUP BY d.venta_id
)
SELECT v.fecha_local                                    AS fecha,
       COUNT(*)                                         AS num_ventas,
       SUM(v.subtotal)::numeric(14,2)                   AS subtotal,
       SUM(v.iva)::numeric(14,2)                        AS iva,
       SUM(v.descuento_total)::numeric(14,2)            AS descuentos,
       SUM(v.total)::numeric(14,2)                      AS total_vendido,
       ROUND(AVG(v.total), 2)                           AS ticket_promedio,
       COALESCE(dc.costo_ventas, 0)::numeric(14,2)      AS costo_ventas,
       (SUM(v.subtotal) - COALESCE(dc.costo_ventas, 0))::numeric(14,2) AS utilidad_bruta
FROM ven.ventas v
LEFT JOIN detalle_costo dc ON dc.venta_id = v.venta_id
WHERE v.estado = 'COMPLETADA'
GROUP BY v.fecha_local, dc.costo_ventas
ORDER BY fecha DESC;

-- ===== §16.5 Ingresos (flujo percibido) =====
CREATE OR REPLACE VIEW fin.vw_ingresos AS
SELECT mc.creado_en::date                                            AS fecha,
       mc.concepto,
       COALESCE(fp.nombre, 'EFECTIVO')                               AS forma_pago,
       COUNT(*)                                                      AS num_operaciones,
       SUM(mc.monto)::numeric(14,2)                                  AS monto_total,
       SUM(CASE WHEN COALESCE(fp.es_efectivo, true) THEN mc.monto ELSE 0 END)::numeric(14,2)
                                                                     AS monto_efectivo
FROM fin.movimientos_caja mc
LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
WHERE mc.tipo = 'ENTRADA'
  AND mc.concepto NOT IN ('APERTURA', 'DEPOSITO_GARANTIA_RENTA')
GROUP BY mc.creado_en::date, mc.concepto, COALESCE(fp.nombre, 'EFECTIVO')
ORDER BY fecha DESC, concepto;

-- ===== §16.6 Egresos (flujo pagado) =====
CREATE OR REPLACE VIEW fin.vw_egresos AS
SELECT mc.creado_en::date                                            AS fecha,
       mc.concepto,
       COALESCE(fp.nombre, 'EFECTIVO')                               AS forma_pago,
       COUNT(*)                                                      AS num_operaciones,
       SUM(mc.monto)::numeric(14,2)                                  AS monto_total,
       SUM(CASE WHEN COALESCE(fp.es_efectivo, true) THEN mc.monto ELSE 0 END)::numeric(14,2)
                                                                     AS monto_efectivo
FROM fin.movimientos_caja mc
LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
WHERE mc.tipo = 'SALIDA'
GROUP BY mc.creado_en::date, mc.concepto, COALESCE(fp.nombre, 'EFECTIVO')
ORDER BY fecha DESC, concepto;

-- ===== §16.7 Dinero en caja =====
CREATE OR REPLACE VIEW fin.vw_dinero_en_caja AS
SELECT t.turno_caja_id,
       c.nombre                              AS caja,
       u.username                            AS cajero,
       t.apertura_en::date                   AS fecha,
       t.estado,
       t.monto_apertura::numeric(14,2)       AS fondo_inicial,
       COALESCE(SUM(CASE WHEN mc.tipo = 'ENTRADA'
                          AND COALESCE(fp.es_efectivo, true) THEN mc.monto END), 0)::numeric(14,2)
                                             AS entradas_efectivo,
       COALESCE(SUM(CASE WHEN mc.tipo = 'SALIDA'
                          AND COALESCE(fp.es_efectivo, true) THEN mc.monto END), 0)::numeric(14,2)
                                             AS salidas_efectivo,
       (t.monto_apertura
         + COALESCE(SUM(CASE WHEN mc.tipo = 'ENTRADA'
                             AND COALESCE(fp.es_efectivo, true) THEN mc.monto END), 0)
         - COALESCE(SUM(CASE WHEN mc.tipo = 'SALIDA'
                             AND COALESCE(fp.es_efectivo, true) THEN mc.monto END), 0)
       )::numeric(14,2)                      AS dinero_esperado_en_caja,
       t.monto_contado::numeric(14,2)        AS monto_contado,
       t.diferencia::numeric(14,2)           AS diferencia_corte,
       t.cierre_en
FROM fin.turnos_caja t
JOIN fin.cajas c        ON c.caja_id = t.caja_id
JOIN seg.usuarios u     ON u.usuario_id = t.usuario_id
LEFT JOIN fin.movimientos_caja mc ON mc.turno_caja_id = t.turno_caja_id
LEFT JOIN cat.formas_pago fp      ON fp.forma_pago_id = mc.forma_pago_id
GROUP BY t.turno_caja_id, c.nombre, u.username, t.apertura_en, t.estado,
         t.monto_apertura, t.monto_contado, t.diferencia, t.cierre_en
ORDER BY t.estado DESC, t.apertura_en DESC;

-- ===== §16.8 Cuentas por cobrar =====
CREATE OR REPLACE VIEW ven.vw_cuentas_cobrar AS
SELECT cc.cuenta_cobrar_id,
       v.folio                        AS venta_folio,
       COALESCE(cl.razon_social, 'PÚBLICO GENERAL') AS cliente,
       cl.telefono,
       cc.monto_total,
       cc.monto_pagado,
       (cc.monto_total - cc.monto_pagado)          AS saldo,
       cc.fecha_vencimiento,
       CURRENT_DATE - cc.fecha_vencimiento          AS dias_vencido,
       cc.estado
FROM ven.cuentas_cobrar cc
JOIN ven.ventas v   ON v.venta_id = cc.venta_id
LEFT JOIN ven.clientes cl ON cl.cliente_id = cc.cliente_id
WHERE cc.estado <> 'LIQUIDADA'
ORDER BY dias_vencido DESC NULLS LAST;

-- ===== §16.9 Cuentas por pagar =====
CREATE OR REPLACE VIEW com.vw_cuentas_pagar AS
SELECT cp.cuenta_pagar_id,
       co.folio                       AS compra_folio,
       pv.razon_social                AS proveedor,
       cp.monto_total,
       cp.monto_pagado,
       (cp.monto_total - cp.monto_pagado) AS saldo,
       cp.fecha_vencimiento,
       CURRENT_DATE - cp.fecha_vencimiento AS dias_vencido,
       cp.estado
FROM com.cuentas_pagar cp
JOIN com.compras co     ON co.compra_id = cp.compra_id
JOIN com.proveedores pv ON pv.proveedor_id = co.proveedor_id
WHERE cp.estado <> 'LIQUIDADA'
ORDER BY dias_vencido DESC NULLS LAST;

-- ===== §16.10 Kardex por producto =====
CREATE OR REPLACE VIEW inv.vw_kardex_producto AS
SELECT m.producto_id,
       p.codigo,
       p.nombre                    AS producto,
       a.nombre                    AS almacen,
       m.creado_en,
       m.tipo,
       mo.clave                    AS motivo,
       m.cantidad,
       m.costo_unitario,
       (CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE -m.cantidad END) AS delta,
       SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE -m.cantidad END)
           OVER (PARTITION BY m.producto_id, m.almacen_id
                 ORDER BY m.creado_en, m.movimiento_id)     AS stock_acumulado,
       m.ref_tabla,
       m.ref_id
FROM inv.movimientos_inventario m
JOIN inv.productos p ON p.producto_id = m.producto_id
JOIN inv.almacenes a ON a.almacen_id = m.almacen_id
JOIN cat.motivos_movimiento mo ON mo.motivo_id = m.motivo_id;

-- ===== §21.1 Mejores vendedores =====
CREATE OR REPLACE VIEW ven.vw_mejores_vendedores AS
WITH costo_venta AS (
    SELECT d.venta_id, SUM(d.cantidad * d.costo_unitario) AS costo
    FROM ven.venta_detalles d GROUP BY d.venta_id
)
SELECT date_trunc('month', v.fecha)::date                          AS mes,
       u.usuario_id,
       (e.nombre || ' ' || e.apellido_p)::varchar(161)             AS vendedor,
       COUNT(*)                                                    AS num_ventas,
       SUM(v.total)::numeric(14,2)                                 AS total_vendido,
       ROUND(AVG(v.total), 2)                                      AS ticket_promedio,
       (SUM(v.subtotal) - COALESCE(SUM(c.costo), 0))::numeric(14,2) AS utilidad_generada,
       RANK() OVER (PARTITION BY date_trunc('month', v.fecha)
                    ORDER BY SUM(v.total) DESC)                    AS ranking_mes,
       RANK() OVER (ORDER BY SUM(v.total) DESC)                    AS ranking_historico
FROM ven.ventas v
JOIN seg.usuarios u ON u.usuario_id = v.usuario_id
LEFT JOIN rh.empleados e ON e.empleado_id = u.empleado_id
LEFT JOIN costo_venta c ON c.venta_id = v.venta_id
WHERE v.estado = 'COMPLETADA'
GROUP BY date_trunc('month', v.fecha), u.usuario_id,
         (e.nombre || ' ' || e.apellido_p)
ORDER BY mes DESC, ranking_mes;

-- ===== §21.2 Mejores días de venta =====
CREATE OR REPLACE VIEW ven.vw_mejores_dias_venta AS
SELECT EXTRACT(ISODOW FROM v.fecha)::smallint                       AS dia_num,
       CASE EXTRACT(ISODOW FROM v.fecha)::int
            WHEN 1 THEN 'Lunes'   WHEN 2 THEN 'Martes'  WHEN 3 THEN 'Miércoles'
            WHEN 4 THEN 'Jueves'  WHEN 5 THEN 'Viernes' WHEN 6 THEN 'Sábado'
            ELSE 'Domingo' END                                     AS dia_semana,
       COUNT(DISTINCT v.fecha_local)                               AS dias_con_venta,
       COUNT(*)                                                    AS num_ventas,
       SUM(v.total)::numeric(14,2)                                 AS total_acumulado,
       ROUND(SUM(v.total) / NULLIF(COUNT(DISTINCT v.fecha_local), 0), 2)
                                                                   AS promedio_por_dia,
       RANK() OVER (ORDER BY SUM(v.total)
                    / NULLIF(COUNT(DISTINCT v.fecha_local), 0) DESC) AS ranking
FROM ven.ventas v
WHERE v.estado = 'COMPLETADA'
GROUP BY EXTRACT(ISODOW FROM v.fecha)
ORDER BY ranking;

-- ===== §21.3 Ventas por hora (+ matriz día × hora) =====
CREATE OR REPLACE VIEW ven.vw_ventas_por_hora AS
SELECT EXTRACT(HOUR FROM v.fecha)::smallint                         AS hora,
       COUNT(*)                                                     AS num_ventas,
       SUM(v.total)::numeric(14,2)                                  AS total_acumulado,
       ROUND(AVG(v.total), 2)                                       AS ticket_promedio,
       RANK() OVER (ORDER BY SUM(v.total) DESC)                     AS ranking_horario
FROM ven.ventas v
WHERE v.estado = 'COMPLETADA'
GROUP BY EXTRACT(HOUR FROM v.fecha)
ORDER BY hora;

CREATE OR REPLACE VIEW ven.vw_ventas_dia_hora AS
SELECT EXTRACT(ISODOW FROM v.fecha)::smallint AS dia_num,
       EXTRACT(HOUR  FROM v.fecha)::smallint AS hora,
       COUNT(*)                              AS num_ventas,
       SUM(v.total)::numeric(14,2)           AS total
FROM ven.ventas v
WHERE v.estado = 'COMPLETADA'
GROUP BY 1, 2
ORDER BY dia_num, hora;

-- ===== §21.4 Mejores categorías =====
CREATE OR REPLACE VIEW ven.vw_mejores_categorias AS
WITH det AS (
    SELECT date_trunc('month', v.fecha)::date AS mes,
           d.producto_id, d.cantidad, d.total_linea,
           (d.cantidad * d.costo_unitario) AS costo_linea
    FROM ven.venta_detalles d
    JOIN ven.ventas v ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
)
SELECT d.mes,
       c.categoria_id,
       COALESCE(c.ruta, c.nombre)                                   AS categoria,
       SUM(d.cantidad)::numeric(14,2)                               AS unidades_vendidas,
       SUM(d.total_linea)::numeric(14,2)                            AS ingreso,
       (SUM(d.total_linea) - SUM(d.costo_linea))::numeric(14,2)     AS utilidad,
       RANK() OVER (PARTITION BY d.mes ORDER BY SUM(d.total_linea) DESC) AS ranking_mes,
       RANK() OVER (ORDER BY SUM(d.total_linea) DESC)               AS ranking_historico
FROM det d
JOIN inv.productos p  ON p.producto_id  = d.producto_id
JOIN cat.categorias c ON c.categoria_id = p.categoria_id
GROUP BY d.mes, c.categoria_id, COALESCE(c.ruta, c.nombre)
ORDER BY d.mes DESC, ranking_mes;

-- ===== §21.5 Productos sin movimiento (candidatos a promoción) =====
CREATE OR REPLACE VIEW inv.vw_productos_sin_movimiento AS
WITH ultima_venta AS (
    SELECT m.producto_id, MAX(m.creado_en) AS ultima
    FROM inv.movimientos_inventario m
    JOIN cat.motivos_movimiento mo ON mo.motivo_id = m.motivo_id AND mo.clave = 'VENTA'
    GROUP BY m.producto_id
)
SELECT p.producto_id,
       p.codigo,
       p.nombre                                              AS producto,
       c.nombre                                              AS categoria,
       i.stock,
       p.costo_actual,
       (i.stock * p.costo_actual)::numeric(14,2)             AS dinero_detenido_en_estante,
       uv.ultima                                             AS ultima_venta,
       CASE WHEN uv.ultima IS NULL THEN 9999
            ELSE EXTRACT(day FROM now() - uv.ultima)::int
       END                                                   AS dias_sin_vender,
       CASE WHEN uv.ultima IS NULL                    THEN 'NUNCA_VENDIDO'
            WHEN now() - uv.ultima > interval '90 days' THEN 'CRITICO_MAYOR_90D'
            WHEN now() - uv.ultima > interval '60 days' THEN 'ALTO_MAYOR_60D'
            ELSE 'MODERADO' END                              AS prioridad_promocion
FROM inv.inventario i
JOIN inv.productos p  ON p.producto_id  = i.producto_id
                     AND p.tipo = 'PRODUCTO' AND p.activo
LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
LEFT JOIN ultima_venta uv  ON uv.producto_id = i.producto_id
WHERE i.stock > 0
  AND (uv.ultima IS NULL OR now() - uv.ultima > interval '60 days')
ORDER BY dinero_detenido_en_estante DESC;

-- ===== §21.6 Promociones vigentes y uso de líneas de crédito =====
CREATE OR REPLACE VIEW ven.vw_promociones_vigentes AS
SELECT pr.promocion_id, pr.nombre, pr.tipo,
       pr.valor_pct, pr.valor_monto, pr.precio_especial,
       pr.compra_min_total, pr.compra_min_cantidad, pr.lleva, pr.paga,
       pr.vigencia_hasta, pr.dias_semana, pr.hora_desde, pr.hora_hasta,
       (SELECT COUNT(*) FROM ven.promocion_productos x
         WHERE x.promocion_id = pr.promocion_id)                 AS productos_asignados,
       (pr.max_usos_total - pr.usos_actual)                      AS usos_restantes
FROM ven.promociones pr
WHERE pr.estado = 'ACTIVA'
  AND CURRENT_TIMESTAMP BETWEEN pr.vigencia_desde
                            AND COALESCE(pr.vigencia_hasta, 'infinity'::timestamptz)
  AND EXTRACT(ISODOW FROM CURRENT_TIMESTAMP)::smallint = ANY(pr.dias_semana)
  AND (pr.hora_desde IS NULL OR CURRENT_TIME BETWEEN pr.hora_desde
                                    AND COALESCE(pr.hora_hasta, '23:59:59'::time))
  AND (pr.max_usos_total IS NULL OR pr.usos_actual < pr.max_usos_total)
ORDER BY pr.tipo, pr.nombre;

CREATE OR REPLACE VIEW ven.vw_lineas_credito_uso AS
SELECT lc.linea_credito_id,
       lc.cliente_id,
       cl.razon_social                                          AS cliente,
       lc.monto_autorizado,
       lc.dias_credito,
       lc.estado,
       lc.vigente_hasta,
       COALESCE(u.utilizado, 0)::numeric(12,2)                  AS credito_utilizado,
       (lc.monto_autorizado - COALESCE(u.utilizado, 0))::numeric(12,2)
                                                                AS credito_disponible,
       ROUND(COALESCE(u.utilizado,0) / NULLIF(lc.monto_autorizado,0) * 100, 1)
                                                                AS pct_uso,
       CASE WHEN COALESCE(u.utilizado,0) / NULLIF(lc.monto_autorizado,1) >= 0.8
            THEN 'ALERTA' ELSE 'OK' END                         AS semaforo
FROM ven.lineas_credito lc
JOIN ven.clientes cl ON cl.cliente_id = lc.cliente_id
LEFT JOIN LATERAL (
    SELECT SUM(cc.monto_total - cc.monto_pagado) AS utilizado
    FROM ven.cuentas_cobrar cc
    WHERE cc.cliente_id = lc.cliente_id AND cc.estado IN ('VIGENTE','PARCIAL')
) u ON true
ORDER BY pct_uso DESC NULLS LAST;

-- ===== §21.7 Resumen dashboard (KPIs en una fila) =====
CREATE OR REPLACE VIEW ven.vw_resumen_dashboard AS
SELECT
  (SELECT COALESCE(SUM(total),0) FROM ven.ventas
    WHERE estado='COMPLETADA' AND fecha_local = CURRENT_DATE)::numeric(14,2)
        AS ventas_hoy,
  (SELECT COUNT(*) FROM ven.ventas
    WHERE estado='COMPLETADA' AND fecha_local = CURRENT_DATE)          AS tickets_hoy,
  (SELECT COALESCE(SUM(total),0) FROM ven.ventas
    WHERE estado='COMPLETADA'
      AND date_trunc('month',fecha) = date_trunc('month',CURRENT_TIMESTAMP))::numeric(14,2)
        AS ventas_del_mes,
  (SELECT ROUND(AVG(total),2) FROM ven.ventas
    WHERE estado='COMPLETADA'
      AND date_trunc('month',fecha) = date_trunc('month',CURRENT_TIMESTAMP))
        AS ticket_promedio_mes,
  (SELECT COALESCE(SUM(monto_total - monto_pagado),0) FROM ven.cuentas_cobrar
    WHERE estado IN ('VIGENTE','PARCIAL'))::numeric(14,2)              AS saldo_por_cobrar,
  (SELECT COALESCE(SUM(monto_total - monto_pagado),0) FROM ven.cuentas_cobrar
    WHERE estado IN ('VIGENTE','PARCIAL')
      AND fecha_vencimiento < CURRENT_DATE)::numeric(14,2)             AS cobranza_vencida,
  (SELECT COALESCE(SUM(i.stock * p.costo_actual),0)
     FROM inv.inventario i JOIN inv.productos p ON p.producto_id = i.producto_id
     WHERE p.tipo = 'PRODUCTO')::numeric(14,2)                         AS valor_inventario,
  (SELECT COUNT(*) FROM inv.vw_stock_bajo WHERE alerta = 'AGOTADO')    AS productos_agotados,
  (SELECT COUNT(*) FROM ven.promociones
    WHERE estado='ACTIVA' AND CURRENT_TIMESTAMP BETWEEN vigencia_desde
          AND COALESCE(vigencia_hasta,'infinity'::timestamptz))         AS promociones_activas,
  (SELECT COUNT(*) FROM fin.turnos_caja WHERE estado='ABIERTO')        AS cajas_abiertas;

-- ===== §21.8 Facturas de proveedores: últimas 15, vencidas y pendientes =====
CREATE OR REPLACE VIEW com.vw_ultimas_facturas_proveedor AS
WITH facturas AS (
    SELECT co.compra_id,
           co.folio                       AS compra_folio,
           co.factura_proveedor,
           pv.proveedor_id,
           pv.razon_social                AS proveedor,
           co.fecha::date                 AS fecha,
           co.subtotal,
           co.iva,
           co.total,
           COALESCE(cp.monto_total, co.total)::numeric(14,2)      AS monto_total,
           COALESCE(cp.monto_pagado, 0)::numeric(14,2)            AS monto_pagado,
           (COALESCE(cp.monto_total, co.total) - COALESCE(cp.monto_pagado, 0))::numeric(14,2)
                                                                                  AS saldo,
           cp.estado                                                              AS estado_pago,
           cp.fecha_vencimiento,
           ROW_NUMBER() OVER (PARTITION BY pv.proveedor_id
                              ORDER BY co.fecha DESC, co.compra_id DESC)          AS rn
    FROM com.compras co
    JOIN com.proveedores pv     ON pv.proveedor_id = co.proveedor_id
    LEFT JOIN com.cuentas_pagar cp ON cp.compra_id = co.compra_id
    WHERE co.estado <> 'CANCELADA'
)
SELECT rn                          AS numero_mas_reciente,
       proveedor_id, proveedor, compra_folio, factura_proveedor,
       fecha, subtotal, iva, total, monto_total, monto_pagado, saldo,
       estado_pago, fecha_vencimiento
FROM facturas
WHERE rn <= 15
ORDER BY proveedor, rn;
CREATE OR REPLACE VIEW com.vw_facturas_vencidas AS
SELECT cp.cuenta_pagar_id,
       co.folio                     AS compra_folio,
       co.factura_proveedor,
       pv.proveedor_id,
       pv.razon_social              AS proveedor,
       pv.telefono                  AS contacto_telefono,
       co.fecha::date               AS fecha_compra,
       cp.monto_total,
       cp.monto_pagado,
       (cp.monto_total - cp.monto_pagado)::numeric(14,2) AS saldo,
       cp.fecha_vencimiento,
       (CURRENT_DATE - cp.fecha_vencimiento)             AS dias_vencido,
       CASE
            WHEN CURRENT_DATE - cp.fecha_vencimiento <= 30 THEN 'MORA_1_30'
            WHEN CURRENT_DATE - cp.fecha_vencimiento <= 60 THEN 'MORA_31_60'
            WHEN CURRENT_DATE - cp.fecha_vencimiento <= 90 THEN 'MORA_61_90'
            ELSE 'MORA_MAS_90'
       END                                               AS antiguedad
FROM com.cuentas_pagar cp
JOIN com.compras co      ON co.compra_id    = cp.compra_id AND co.estado <> 'CANCELADA'
JOIN com.proveedores pv  ON pv.proveedor_id = co.proveedor_id
WHERE cp.estado IN ('VIGENTE','PARCIAL')
  AND cp.fecha_vencimiento < CURRENT_DATE
ORDER BY dias_vencido DESC, saldo DESC;

CREATE OR REPLACE VIEW com.vw_facturas_pendientes AS
SELECT cp.cuenta_pagar_id,
       co.folio                     AS compra_folio,
       co.factura_proveedor,
       pv.proveedor_id,
       pv.razon_social              AS proveedor,
       co.fecha::date               AS fecha_compra,
       cp.monto_total,
       cp.monto_pagado,
       (cp.monto_total - cp.monto_pagado)::numeric(14,2) AS saldo,
       cp.estado                    AS estado_pago,
       cp.fecha_vencimiento,
       (cp.fecha_vencimiento - CURRENT_DATE)             AS dias_para_vencer,
       CASE
            WHEN cp.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDA'
            WHEN cp.fecha_vencimiento <= CURRENT_DATE + 5 THEN 'POR_VENCER'
            ELSE 'CORRIENTE'
       END                                               AS alerta
FROM com.cuentas_pagar cp
JOIN com.compras co      ON co.compra_id    = cp.compra_id AND co.estado <> 'CANCELADA'
JOIN com.proveedores pv  ON pv.proveedor_id = co.proveedor_id
WHERE cp.estado IN ('VIGENTE','PARCIAL')
ORDER BY cp.fecha_vencimiento, saldo DESC;

GRANT SELECT ON ALL TABLES IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    TO ferreteria_app;

-- ===== Cortes de caja: histórico y cierre diario =====
CREATE OR REPLACE VIEW fin.vw_historico_cortes AS
SELECT c.corte_id,
       c.fecha,
       ca.nombre                    AS caja,
       a.nombre                     AS almacen,
       uc.username                  AS cerrado_por,
       c.apertura_en,
       c.cierre_en,
       EXTRACT(epoch FROM (c.cierre_en - c.apertura_en))/3600 AS horas_turno,
       c.num_ventas,
       c.subtotal,
       c.iva,
       c.descuentos,
       c.total_vendido,
       c.costo_ventas,
       c.utilidad_bruta,
       c.margen_pct,
       c.perdidas_inventario        AS perdidas,
       c.fondo_apertura,
       c.entradas_efectivo,
       c.salidas_efectivo,
       c.ingresos_no_efectivo,
       c.egresos_no_efectivo,
       c.dinero_esperado,
       c.dinero_contado,
       c.diferencia,
       CASE WHEN c.diferencia = 0 THEN 'CUADRADO'
            WHEN c.diferencia > 0  THEN 'SOBRANTE'
            ELSE 'FALTANTE' END    AS resultado_caja,
       c.desglose_entradas,
       c.desglose_salidas,
       c.desglose_formas_pago,
       c.observaciones
FROM fin.cortes_caja c
JOIN fin.cajas ca      ON ca.caja_id    = c.caja_id
JOIN inv.almacenes a   ON a.almacen_id  = c.almacen_id
JOIN seg.usuarios uc   ON uc.usuario_id = c.usuario_cierre_id;

CREATE OR REPLACE VIEW fin.vw_cierre_diario AS
SELECT fecha,
       COUNT(*)                                  AS num_cortes,
       SUM(num_ventas)                           AS tickets,
       SUM(total_vendido)::numeric(14,2)         AS total_vendido,
       SUM(utilidad_bruta)::numeric(14,2)        AS utilidad_bruta,
       ROUND(SUM(subtotal) / NULLIF(SUM(costo_ventas),0) * 100 - 100, 2) AS margen_pct_promedio,
       SUM(perdidas_inventario)::numeric(14,2)   AS perdidas,
       SUM(entradas_efectivo)::numeric(14,2)     AS entradas_efectivo,
       SUM(salidas_efectivo)::numeric(14,2)      AS salidas_efectivo,
       SUM(dinero_contado)::numeric(14,2)        AS efectivo_depositado,
       SUM(diferencia)::numeric(14,2)            AS diferencia_total,
       SUM(ingresos_no_efectivo)::numeric(14,2)  AS ingresos_digitales,
       BOOL_AND(diferencia = 0)                  AS todo_cuadrado
FROM fin.cortes_caja
GROUP BY fecha
ORDER BY fecha DESC;
