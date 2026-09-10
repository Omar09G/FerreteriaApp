-- V14: Corrige ven.fn_promo_para_producto para DESCUENTO_TOTAL_VENTA
-- Antes devolvía siempre 0 para ese tipo (ELSE 0). Ahora calcula beneficio y respeta compra_min_total.
CREATE OR REPLACE FUNCTION ven.fn_promo_para_producto(
    p_producto BIGINT, p_cantidad NUMERIC,
    p_precio_unit NUMERIC, p_cliente BIGINT DEFAULT NULL
)
RETURNS TABLE (promo_id BIGINT, promo_nombre TEXT, promo_tipo TEXT,
               beneficio NUMERIC, detalle TEXT)
LANGUAGE plpgsql STABLE AS $$
BEGIN
    RETURN QUERY
    SELECT pr.promocion_id,
           pr.nombre::TEXT,
           pr.tipo::TEXT,
           CASE pr.tipo
               WHEN 'PRECIO_ESPECIAL' THEN
                   GREATEST(p_precio_unit - COALESCE(pr.precio_especial, 0), 0) * p_cantidad
               WHEN 'DESCUENTO_PRODUCTO' THEN
                   COALESCE(pr.valor_pct/100 * p_precio_unit * p_cantidad, pr.valor_monto)
               WHEN 'DESCUENTO_TOTAL_VENTA' THEN
                   CASE 
                     WHEN pr.compra_min_total IS NOT NULL AND p_precio_unit * p_cantidad < pr.compra_min_total THEN 0
                     ELSE COALESCE(pr.valor_pct/100 * p_precio_unit * p_cantidad, pr.valor_monto)
                   END
               WHEN 'POR_CANTIDAD' THEN
                   CASE WHEN p_cantidad >= COALESCE(pr.compra_min_cantidad, 0)
                        THEN COALESCE(pr.valor_pct/100 * p_precio_unit * p_cantidad,
                                      pr.valor_monto)
                        ELSE 0 END
               WHEN 'NXM' THEN
                   FLOOR(p_cantidad / NULLIF(pr.lleva, 0))
                     * (pr.lleva - pr.paga) * p_precio_unit
               ELSE 0 END::numeric AS beneficio,
           ('[' || pr.tipo || '] ' || COALESCE(pr.descripcion, ''))::TEXT
    FROM ven.promociones pr
    LEFT JOIN ven.promocion_productos pp
           ON pp.promocion_id = pr.promocion_id AND pp.producto_id = p_producto
    LEFT JOIN ven.promocion_categorias pc
           ON pc.promocion_id = pr.promocion_id
          AND pc.categoria_id = (SELECT categoria_id FROM inv.productos
                                 WHERE producto_id = p_producto)
    WHERE pr.estado = 'ACTIVA'
      AND CURRENT_TIMESTAMP BETWEEN pr.vigencia_desde
                                AND COALESCE(pr.vigencia_hasta, 'infinity'::timestamptz)
      AND EXTRACT(ISODOW FROM CURRENT_TIMESTAMP)::smallint = ANY(pr.dias_semana)
      AND (pr.hora_desde IS NULL OR CURRENT_TIME BETWEEN pr.hora_desde
                                    AND COALESCE(pr.hora_hasta, '23:59:59'::time))
      AND (pp.producto_id IS NOT NULL OR pc.categoria_id IS NOT NULL)
      AND (pr.compra_min_cantidad IS NULL OR p_cantidad >= pr.compra_min_cantidad)
      AND (NOT pr.solo_mayoristas OR EXISTS (
              SELECT 1 FROM ven.clientes c
              WHERE c.cliente_id = p_cliente AND c.es_mayorista))
      AND (pr.max_usos_total IS NULL OR pr.usos_actual < pr.max_usos_total)
    ORDER BY 4 DESC
    LIMIT 1;
END $$;
