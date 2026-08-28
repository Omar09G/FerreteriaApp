package mx.ferreteria.api.ven.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.ven.dto.ReportDtos;

/**
 * Reportes y dashboard (PLAN §16/§21). Todas las consultas reciben rango
 * [fechaInicio, fechaFin] (default: hoy) para consultar operaciones de un día
 * o periodo en específico y acotar la lectura a índices por fecha
 * (performance). Los rankings se recalculan DENTRO del rango solicitado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private final JdbcTemplate jdbc;

    public List<ReportDtos.TopProductoResponse> topProductos(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            """
            SELECT ?::date AS mes, p.producto_id, p.codigo, p.nombre AS producto,
                   c.nombre AS categoria,
                   SUM(d.cantidad)::numeric(14,2)                       AS unidades_vendidas,
                   SUM(d.total_linea)::numeric(14,2)                    AS ingreso_total,
                   SUM(d.cantidad * d.costo_unitario)::numeric(14,2)    AS costo_total,
                   (SUM(d.total_linea) - SUM(d.cantidad * d.costo_unitario))::numeric(14,2)
                                                                        AS utilidad,
                   RANK() OVER (ORDER BY SUM(d.total_linea) DESC)       AS ranking_mes,
                   RANK() OVER (ORDER BY SUM(d.cantidad) DESC)          AS ranking_unidades
            FROM ven.venta_detalles d
            JOIN ven.ventas v ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
            JOIN inv.productos p ON p.producto_id = d.producto_id
            LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
            WHERE v.fecha_local BETWEEN ? AND ?
            GROUP BY p.producto_id, p.codigo, p.nombre, c.nombre
            ORDER BY ingreso_total DESC
            LIMIT 20
            """,
            new BeanPropertyRowMapper<>(ReportDtos.TopProductoResponse.class),
            inicio, inicio, fin);
    }

    public List<ReportDtos.MejorClienteResponse> mejoresClientes(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            """
            SELECT ?::date AS mes, cl.cliente_id, cl.razon_social AS cliente,
                   COUNT(DISTINCT v.venta_id)                       AS num_compras,
                   SUM(v.total)::numeric(14,2)                      AS total_comprado,
                   ROUND(AVG(v.total), 2)                           AS ticket_promedio,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)         AS ranking_mes,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)         AS ranking_historico
            FROM ven.ventas v
            JOIN ven.clientes cl ON cl.cliente_id = v.cliente_id
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN ? AND ?
            GROUP BY cl.cliente_id, cl.razon_social
            ORDER BY total_comprado DESC
            LIMIT 20
            """,
            new BeanPropertyRowMapper<>(ReportDtos.MejorClienteResponse.class),
            inicio, inicio, fin);
    }

    public List<ReportDtos.VentaTotalResponse> ventasTotales(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            "SELECT * FROM ven.vw_ventas_totales WHERE fecha BETWEEN ? AND ? ORDER BY fecha",
            new BeanPropertyRowMapper<>(ReportDtos.VentaTotalResponse.class),
            inicio, fin);
    }

    public List<ReportDtos.MejorVendedorResponse> mejoresVendedores(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            """
            WITH costo_venta AS (
                SELECT d.venta_id, SUM(d.cantidad * d.costo_unitario) AS costo
                FROM ven.venta_detalles d GROUP BY d.venta_id
            )
            SELECT ?::date AS mes, u.usuario_id,
                   (e.nombre || ' ' || e.apellido_p)::varchar(161) AS vendedor,
                   COUNT(*)                                        AS num_ventas,
                   SUM(v.total)::numeric(14,2)                     AS total_vendido,
                   ROUND(AVG(v.total), 2)                          AS ticket_promedio,
                   (SUM(v.subtotal) - COALESCE(SUM(c.costo), 0))::numeric(14,2)
                                                                   AS utilidad_generada,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)        AS ranking_mes,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)        AS ranking_historico
            FROM ven.ventas v
            JOIN seg.usuarios u ON u.usuario_id = v.usuario_id
            LEFT JOIN rh.empleados e ON e.empleado_id = u.empleado_id
            LEFT JOIN costo_venta c ON c.venta_id = v.venta_id
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN ? AND ?
            GROUP BY u.usuario_id, (e.nombre || ' ' || e.apellido_p)
            ORDER BY total_vendido DESC
            LIMIT 20
            """,
            new BeanPropertyRowMapper<>(ReportDtos.MejorVendedorResponse.class),
            inicio, inicio, fin);
    }

    public List<ReportDtos.VentaPorHoraResponse> ventasPorHora(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            """
            SELECT EXTRACT(HOUR FROM v.fecha)::smallint AS hora,
                   COUNT(*)                             AS num_ventas,
                   SUM(v.total)::numeric(14,2)          AS total_acumulado,
                   ROUND(AVG(v.total), 2)               AS ticket_promedio,
                   RANK() OVER (ORDER BY SUM(v.total) DESC) AS ranking_horario
            FROM ven.ventas v
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN ? AND ?
            GROUP BY EXTRACT(HOUR FROM v.fecha)
            ORDER BY hora
            """,
            new BeanPropertyRowMapper<>(ReportDtos.VentaPorHoraResponse.class),
            inicio, fin);
    }

    public List<ReportDtos.MejorDiaVentaResponse> mejoresDiasVenta(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            """
            SELECT EXTRACT(ISODOW FROM v.fecha)::smallint AS dia_num,
                   CASE EXTRACT(ISODOW FROM v.fecha)::int
                        WHEN 1 THEN 'Lunes'   WHEN 2 THEN 'Martes'  WHEN 3 THEN 'Miércoles'
                        WHEN 4 THEN 'Jueves'  WHEN 5 THEN 'Viernes' WHEN 6 THEN 'Sábado'
                        ELSE 'Domingo' END                     AS dia_semana,
                   COUNT(DISTINCT v.fecha_local)              AS dias_con_venta,
                   COUNT(*)                                   AS num_ventas,
                   SUM(v.total)::numeric(14,2)                AS total_acumulado,
                   ROUND(SUM(v.total) / NULLIF(COUNT(DISTINCT v.fecha_local), 0), 2)
                                                               AS promedio_por_dia,
                   RANK() OVER (ORDER BY SUM(v.total)
                       / NULLIF(COUNT(DISTINCT v.fecha_local), 0) DESC) AS ranking
            FROM ven.ventas v
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN ? AND ?
            GROUP BY EXTRACT(ISODOW FROM v.fecha)
            ORDER BY ranking
            """,
            new BeanPropertyRowMapper<>(ReportDtos.MejorDiaVentaResponse.class),
            inicio, fin);
    }

    public ReportDtos.ResumenDashboardResponse resumenDashboard(LocalDate inicio, LocalDate fin) {
        return jdbc.queryForObject(
            """
            SELECT
              (SELECT COALESCE(SUM(total), 0) FROM ven.ventas
                WHERE estado = 'COMPLETADA' AND fecha_local BETWEEN ? AND ?)::numeric(14,2)
                  AS ventas_en_rango,
              (SELECT COUNT(*) FROM ven.ventas
                WHERE estado = 'COMPLETADA' AND fecha_local BETWEEN ? AND ?) AS tickets_en_rango,
              (SELECT ROUND(AVG(total), 2) FROM ven.ventas
                WHERE estado = 'COMPLETADA' AND fecha_local BETWEEN ? AND ?) AS ticket_promedio_en_rango,
              (SELECT COALESCE(SUM(monto_total - monto_pagado), 0) FROM ven.cuentas_cobrar
                WHERE estado IN ('VIGENTE', 'PARCIAL'))::numeric(14,2)       AS saldo_por_cobrar,
              (SELECT COALESCE(SUM(monto_total - monto_pagado), 0) FROM ven.cuentas_cobrar
                WHERE estado IN ('VIGENTE', 'PARCIAL')
                  AND fecha_vencimiento < CURRENT_DATE)::numeric(14,2)       AS cobranza_vencida,
              (SELECT COALESCE(SUM(i.stock * p.costo_actual), 0)
                 FROM inv.inventario i JOIN inv.productos p ON p.producto_id = i.producto_id
                 WHERE p.tipo = 'PRODUCTO')::numeric(14,2)                   AS valor_inventario,
              (SELECT COUNT(*) FROM inv.vw_stock_bajo WHERE alerta = 'AGOTADO')
                                                                            AS productos_agotados,
              (SELECT COUNT(*) FROM ven.promociones
                WHERE estado = 'ACTIVA' AND CURRENT_TIMESTAMP BETWEEN vigencia_desde
                  AND COALESCE(vigencia_hasta, 'infinity'::timestamptz))    AS promociones_activas,
              (SELECT COUNT(*) FROM fin.turnos_caja WHERE estado = 'ABIERTO')
                                                                            AS cajas_abiertas
            """,
            new BeanPropertyRowMapper<>(ReportDtos.ResumenDashboardResponse.class),
            inicio, fin, inicio, fin, inicio, fin);
    }

    public List<ReportDtos.CierreDiarioResponse> cierreDiario(LocalDate inicio, LocalDate fin) {
        return jdbc.query(
            "SELECT * FROM fin.vw_cierre_diario WHERE fecha BETWEEN ? AND ? ORDER BY fecha",
            new BeanPropertyRowMapper<>(ReportDtos.CierreDiarioResponse.class),
            inicio, fin);
    }
}