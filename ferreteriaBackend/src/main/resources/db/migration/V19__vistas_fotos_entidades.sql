-- ============================================================================
-- V19__vistas_fotos_entidades.sql
-- Expone foto_url/imagen_url en las vistas que listan productos, clientes,
-- proveedores y empleados. Espejo de scripts/vistas_core.sql (solo estas
-- 11 vistas; el resto no cambia). CREATE OR REPLACE conserva grants.
-- Columnas agregadas al FINAL de cada vista: exigencia de CREATE OR REPLACE
-- (no renombra ni desplaza columnas) y protege lectores posicionales
-- (SELECT * + Object[]); los DTOs com/ven ya leen el extra.
-- ============================================================================

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
                    ORDER BY SUM(d.cantidad) DESC)            AS ranking_unidades,
       p.imagen_url                                           AS imagen_url
FROM ven.venta_detalles d
JOIN ven.ventas v    ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
JOIN inv.productos p ON p.producto_id = d.producto_id
LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
GROUP BY date_trunc('month', v.fecha), p.producto_id, p.codigo, p.nombre, p.imagen_url, c.nombre;

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
       RANK() OVER (ORDER BY SUM(v.total) DESC)               AS ranking_historico,
       cl.foto_url                                            AS foto_url
FROM ven.ventas v
JOIN ven.clientes cl ON cl.cliente_id = v.cliente_id
WHERE v.estado = 'COMPLETADA'
GROUP BY date_trunc('month', v.fecha), cl.cliente_id, cl.razon_social, cl.foto_url;

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
       CASE WHEN i.stock <= 0 THEN 'AGOTADO' ELSE 'BAJO' END AS alerta,
       p.imagen_url                AS imagen_url
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
       cc.estado,
       cl.foto_url                  AS foto_url
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
       cp.estado,
       pv.foto_url                    AS foto_url
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
       m.ref_id,
       p.imagen_url                AS imagen_url
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
       RANK() OVER (ORDER BY SUM(v.total) DESC)                    AS ranking_historico,
       e.foto_url                                                 AS foto_url
FROM ven.ventas v
JOIN seg.usuarios u ON u.usuario_id = v.usuario_id
LEFT JOIN rh.empleados e ON e.empleado_id = u.empleado_id
LEFT JOIN costo_venta c ON c.venta_id = v.venta_id
WHERE v.estado = 'COMPLETADA'
GROUP BY date_trunc('month', v.fecha), u.usuario_id,
         (e.nombre || ' ' || e.apellido_p), e.foto_url
ORDER BY mes DESC, ranking_mes;

-- ===== §21.2 Mejores días de venta =====

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
            ELSE 'MODERADO' END                              AS prioridad_promocion,
       p.imagen_url                                          AS imagen_url
FROM inv.inventario i
JOIN inv.productos p  ON p.producto_id  = i.producto_id
                     AND p.tipo = 'PRODUCTO' AND p.activo
LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
LEFT JOIN ultima_venta uv  ON uv.producto_id = i.producto_id
WHERE i.stock > 0
  AND (uv.ultima IS NULL OR now() - uv.ultima > interval '60 days')
ORDER BY dinero_detenido_en_estante DESC;

-- ===== §21.6 Promociones vigentes y uso de líneas de crédito =====

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
       estado_pago, fecha_vencimiento,
       (SELECT pv2.foto_url FROM com.proveedores pv2 WHERE pv2.proveedor_id = facturas.proveedor_id)
                                        AS foto_url
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
       END                                               AS antiguedad,
       pv.foto_url                                       AS foto_url
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
       END                                               AS alerta,
       pv.foto_url                                       AS foto_url
FROM com.cuentas_pagar cp
JOIN com.compras co      ON co.compra_id    = cp.compra_id AND co.estado <> 'CANCELADA'
JOIN com.proveedores pv  ON pv.proveedor_id = co.proveedor_id
WHERE cp.estado IN ('VIGENTE','PARCIAL')
ORDER BY cp.fecha_vencimiento, saldo DESC;

GRANT SELECT ON ALL TABLES IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    TO ferreteria_app;

-- ===== Cortes de caja: histórico y cierre diario =====
