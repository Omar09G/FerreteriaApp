package mx.ferreteria.api.ven.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.ven.dto.ReportDtos;
import mx.ferreteria.api.ven.entity.Venta;

/**
 * Consultas nativas del módulo ven ejecutadas desde {@code ReporteService}.
 * Centraliza aquí las queries que antes vivían en el service con
 * {@code JdbcTemplate} para que toda la lógica de persistencia del dashboard
 * quede dentro de repositorios.
 *
 * <p>Se extiende {@link JpaRepository} únicamente para que Spring Data detecte
 * la interfaz como repositorio; los métodos CRUD heredados no se usan.</p>
 *
 * <p>Los aliases de cada SELECT ya están en camelCase para que
 * Spring Data pueda mapear las filas a los records de
 * {@link ReportDtos} sin transformación adicional.</p>
 */
public interface ReporteRepository extends JpaRepository<Venta, Long> {

    /**
     * Top 20 productos por ingreso en el rango, con ranking por ingreso y por
     * unidades. Acota a ventas COMPLETADAS y respeta los índices por
     * {@code fecha_local}.
     */
    @Query(value = """
            SELECT CAST(:inicio AS date) AS mes, p.producto_id AS productoId,
                   p.codigo, p.nombre AS producto,
                   c.nombre AS categoria,
                   SUM(d.cantidad)::numeric(14,2)                       AS unidadesVendidas,
                   SUM(d.total_linea)::numeric(14,2)                    AS ingresoTotal,
                   SUM(d.cantidad * d.costo_unitario)::numeric(14,2)    AS costoTotal,
                   (SUM(d.total_linea) - SUM(d.cantidad * d.costo_unitario))::numeric(14,2)
                                                                                    AS utilidad,
                   RANK() OVER (ORDER BY SUM(d.total_linea) DESC)       AS rankingMes,
                   RANK() OVER (ORDER BY SUM(d.cantidad) DESC)          AS rankingUnidades
            FROM ven.venta_detalles d
            JOIN ven.ventas v ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
            JOIN inv.productos p ON p.producto_id = d.producto_id
            LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
            WHERE v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY p.producto_id, p.codigo, p.nombre, c.nombre
            ORDER BY ingresoTotal DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Tuple> findTopProductosRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente que expone el Record de forma limpia a tus servicios
    default List<ReportDtos.TopProductoResponse> findTopProductos(LocalDate inicio, LocalDate fin) {
        return findTopProductosRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.TopProductoResponse(
                        // Convierte el java.sql.Date nativo a LocalDate de Java 8+
                        tuple.get("mes", java.sql.Date.class).toLocalDate(),
                        // p.producto_id (Ajustar si tu ID no es Long/Integer en tu BD)
                        ((Number) tuple.get("productoId")).longValue(),
                        tuple.get("codigo", String.class),
                        tuple.get("producto", String.class),
                        tuple.get("categoria", String.class),
                        tuple.get("unidadesVendidas", BigDecimal.class),
                        tuple.get("ingresoTotal", BigDecimal.class),
                        tuple.get("costoTotal", BigDecimal.class),
                        tuple.get("utilidad", BigDecimal.class),
                        // Evita ClassCastException si RANK() viene como Integer o Long
                        ((Number) tuple.get("rankingMes")).longValue(),
                        ((Number) tuple.get("rankingUnidades")).longValue()
                ))
                .collect(Collectors.toList());
    }


    /**
     * Top 20 clientes por total comprado en el rango, con ranking por mes y
     * acumulado histórico.
     */

    @Query(value = """
            SELECT CAST(:inicio AS date) AS mes, cl.cliente_id AS clienteId,
                   cl.razon_social AS cliente,
                   COUNT(DISTINCT v.venta_id)                       AS numCompras,
                   SUM(v.total)::numeric(14,2)                      AS totalComprado,
                   ROUND(AVG(v.total), 2)                           AS ticketPromedio,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)         AS rankingMes,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)         AS rankingHistorico
            FROM ven.ventas v
            JOIN ven.clientes cl ON cl.cliente_id = v.cliente_id
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY cl.cliente_id, cl.razon_social
            ORDER BY totalComprado DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Tuple> findMejoresClientesRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.MejorClienteResponse> findMejoresClientes(LocalDate inicio, LocalDate fin) {
        return findMejoresClientesRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.MejorClienteResponse(
                        // Extrae java.sql.Date y convierte a LocalDate
                        tuple.get("mes", java.sql.Date.class).toLocalDate(),
                        // cl.cliente_id (Casteo numérico seguro)
                        ((Number) tuple.get("clienteId")).longValue(),
                        tuple.get("cliente", String.class),
                        // COUNT(DISTINCT...) casteado de forma segura a Long
                        ((Number) tuple.get("numCompras")).longValue(),
                        tuple.get("totalComprado", java.math.BigDecimal.class),
                        // ROUND(AVG(...)) interpretado como BigDecimal para precisión monetaria
                        tuple.get("ticketPromedio", java.math.BigDecimal.class),
                        // RANK() casteados a Long de forma segura
                        ((Number) tuple.get("rankingMes")).longValue(),
                        ((Number) tuple.get("rankingHistorico")).longValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Ventas diarias totales (vista {@code ven.vw_ventas_totales}) acotadas al
     * rango solicitado. Los aliases se exponen explícitamente para que Spring
     * Data mapee cada fila al record {@link ReportDtos.VentaTotalResponse}.
     */
    @Query(value = """
            SELECT fecha,
                   num_ventas AS numVentas,
                   subtotal, iva, descuentos,
                   total_vendido AS totalVendido,
                   ticket_promedio AS ticketPromedio,
                   costo_ventas AS costoVentas,
                   utilidad_bruta AS utilidadBruta
            FROM ven.vw_ventas_totales
            WHERE fecha BETWEEN :inicio AND :fin
            ORDER BY fecha
            """, nativeQuery = true)
    List<Tuple> findVentasTotalesRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Este método puente resolverá el error de conversión de forma transparente
    default List<ReportDtos.VentaTotalResponse> findVentasTotales(LocalDate inicio, LocalDate fin) {
        return findVentasTotalesRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.VentaTotalResponse(
                        // Dependiendo de tu BD, la fecha puede requerir java.sql.Date o java.time.LocalDate
                        tuple.get("fecha", java.sql.Date.class).toLocalDate(),
                        // Forzamos el casteo numérico seguro por si la BD devuelve BigInteger/Integer
                        ((Number) tuple.get("numVentas")).longValue(),
                        tuple.get("subtotal", BigDecimal.class),
                        tuple.get("iva", BigDecimal.class),
                        tuple.get("descuentos", BigDecimal.class),
                        tuple.get("totalVendido", BigDecimal.class),
                        tuple.get("ticketPromedio", BigDecimal.class),
                        tuple.get("costoVentas", BigDecimal.class),
                        tuple.get("utilidadBruta", BigDecimal.class)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Top 20 vendedores por total vendido con CTE de costo por venta y
     * ranking por mes / histórico.
     */
    @Query(value = """
            WITH costo_venta AS (
                SELECT d.venta_id, SUM(d.cantidad * d.costo_unitario) AS costo
                FROM ven.venta_detalles d GROUP BY d.venta_id
            )
            SELECT CAST(:inicio AS date) AS mes, u.usuario_id AS usuarioId,
                   (e.nombre || ' ' || e.apellido_p)::varchar(161) AS vendedor,
                   COUNT(*)                                        AS numVentas,
                   SUM(v.total)::numeric(14,2)                     AS totalVendido,
                   ROUND(AVG(v.total), 2)                          AS ticketPromedio,
                   (SUM(v.subtotal) - COALESCE(SUM(c.costo), 0))::numeric(14,2)
                                                                          AS utilidadGenerada,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)        AS rankingMes,
                   RANK() OVER (ORDER BY SUM(v.total) DESC)        AS rankingHistorico
            FROM ven.ventas v
            JOIN seg.usuarios u ON u.usuario_id = v.usuario_id
            LEFT JOIN rh.empleados e ON e.empleado_id = u.empleado_id
            LEFT JOIN costo_venta c ON c.venta_id = v.venta_id
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY u.usuario_id, (e.nombre || ' ' || e.apellido_p)
            ORDER BY totalVendido DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Tuple> findMejoresVendedoresRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.MejorVendedorResponse> findMejoresVendedores(LocalDate inicio, LocalDate fin) {
        return findMejoresVendedoresRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.MejorVendedorResponse(
                        // Extrae java.sql.Date nativo y convierte a LocalDate
                        tuple.get("mes", java.sql.Date.class).toLocalDate(),
                        // u.usuario_id (Casteo seguro)
                        (int) ((Number) tuple.get("usuarioId")).longValue(),
                        tuple.get("vendedor", String.class),
                        // COUNT(*) mapeado de forma segura a Long
                        ((Number) tuple.get("numVentas")).longValue(),
                        tuple.get("totalVendido", java.math.BigDecimal.class),
                        // ROUND(AVG(...)) interpretado como BigDecimal
                        tuple.get("ticketPromedio", java.math.BigDecimal.class),
                        tuple.get("utilidadGenerada", java.math.BigDecimal.class),
                        // RANK() casteados a Long de forma segura
                        ((Number) tuple.get("rankingMes")).longValue(),
                        ((Number) tuple.get("rankingHistorico")).longValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Ventas agrupadas por hora del día con ranking por total acumulado.
     */
    @Query(value = """
            SELECT EXTRACT(HOUR FROM v.fecha)::smallint AS hora,
                   COUNT(*)                             AS numVentas,
                   SUM(v.total)::numeric(14,2)          AS totalAcumulado,
                   ROUND(AVG(v.total), 2)               AS ticketPromedio,
                   RANK() OVER (ORDER BY SUM(v.total) DESC) AS rankingHorario
            FROM ven.ventas v
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY EXTRACT(HOUR FROM v.fecha)
            ORDER BY hora
            """, nativeQuery = true)
    List<Tuple> findVentasPorHoraRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.VentaPorHoraResponse> findVentasPorHora(LocalDate inicio, LocalDate fin) {
        return findVentasPorHoraRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.VentaPorHoraResponse(
                        // Extrae el valor numérico de la hora de forma segura como Integer
                        ((Number) tuple.get("hora")).intValue(),
                        // COUNT(*) mapeado de forma segura a Long
                        ((Number) tuple.get("numVentas")).longValue(),
                        tuple.get("totalAcumulado", java.math.BigDecimal.class),
                        // ROUND(AVG(...)) interpretado como BigDecimal
                        tuple.get("ticketPromedio", java.math.BigDecimal.class),
                        // RANK() casteado a Long de forma segura
                        ((Number) tuple.get("rankingHorario")).longValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Mejores días de la semana por promedio diario de venta en el rango.
     */
    @Query(value = """
            SELECT EXTRACT(ISODOW FROM v.fecha)::smallint AS diaNum,
                   CASE EXTRACT(ISODOW FROM v.fecha)::int
                        WHEN 1 THEN 'Lunes'   WHEN 2 THEN 'Martes'  WHEN 3 THEN 'Miércoles'
                        WHEN 4 THEN 'Jueves'  WHEN 5 THEN 'Viernes' WHEN 6 THEN 'Sábado'
                        ELSE 'Domingo' END                     AS diaSemana,
                   COUNT(DISTINCT v.fecha_local)              AS diasConVenta,
                   COUNT(*)                                   AS numVentas,
                   SUM(v.total)::numeric(14,2)                AS totalAcumulado,
                   ROUND(SUM(v.total) / NULLIF(COUNT(DISTINCT v.fecha_local), 0), 2)
                                                                       AS promedioPorDia,
                   RANK() OVER (ORDER BY SUM(v.total)
                       / NULLIF(COUNT(DISTINCT v.fecha_local), 0) DESC) AS ranking
            FROM ven.ventas v
            WHERE v.estado = 'COMPLETADA' AND v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY EXTRACT(ISODOW FROM v.fecha)
            ORDER BY ranking
            """, nativeQuery = true)
    List<Tuple> findMejoresDiasVentaRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.MejorDiaVentaResponse> findMejoresDiasVenta(LocalDate inicio, LocalDate fin) {
        return findMejoresDiasVentaRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.MejorDiaVentaResponse(
                        // Extrae diaNum como un entero estándar de forma segura
                        ((Number) tuple.get("diaNum")).intValue(),
                        tuple.get("diaSemana", String.class),
                        // Campos de agregación COUNT casteados a Long de manera segura
                        ((Number) tuple.get("diasConVenta")).longValue(),
                        ((Number) tuple.get("numVentas")).longValue(),
                        tuple.get("totalAcumulado", java.math.BigDecimal.class),
                        tuple.get("promedioPorDia", java.math.BigDecimal.class),
                        // RANK() casteado a Long
                        ((Number) tuple.get("ranking")).longValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * KPIs del dashboard acotados al rango: ventas, tickets, promedio, cuentas
     * por cobrar (vigentes y vencidas), valor de inventario, productos
     * agotados, promociones activas y cajas abiertas.
     */
    /**
     * Proyeccion native query -> DTO record. Spring Data no mapea Tuple a
     * record Java directamente; devuelve Object[] y construimos el record en
     * el metodo default para evitar ConverterNotFoundException.
     */
    @Query(value = """
            SELECT
                   (SELECT COALESCE(SUM(total), 0) FROM ven.ventas
                    WHERE fecha_local BETWEEN :inicio AND :fin AND estado = 'COMPLETADA')
                                                                              AS ventasEnRango,
                   (SELECT COUNT(*) FROM ven.ventas
                    WHERE fecha_local BETWEEN :inicio AND :fin AND estado = 'COMPLETADA')
                                                                              AS ticketsEnRango,
                   (SELECT COALESCE(SUM(total), 0) FROM ven.ventas
                    WHERE fecha_local BETWEEN :inicio AND :fin AND estado = 'COMPLETADA')
                   / NULLIF((SELECT COUNT(*) FROM ven.ventas
                    WHERE fecha_local BETWEEN :inicio AND :fin AND estado = 'COMPLETADA'), 0)
                                                                              AS ticketPromedioEnRango,
                   (SELECT COALESCE(SUM(monto_total - monto_pagado), 0) FROM ven.cuentas_cobrar
                    WHERE estado IN ('VIGENTE', 'PARCIAL'))                   AS saldoPorCobrar,
                   (SELECT COALESCE(SUM(monto_total - monto_pagado), 0) FROM ven.cuentas_cobrar
                    WHERE estado IN ('VIGENTE', 'PARCIAL')
                      AND fecha_vencimiento < CURRENT_DATE)                 AS cobranzaVencida,
                   (SELECT COALESCE(SUM(stock * costo_actual), 0) FROM inv.inventario i
                    JOIN inv.productos p ON p.producto_id = i.producto_id)
                                                                              AS valorInventario,
                   (SELECT COUNT(*) FROM inv.inventario WHERE stock <= stock_minimo)
                                                                              AS productosAgotados,
                   (SELECT COUNT(*) FROM ven.promociones WHERE estado = 'ACTIVA'
                    AND CURRENT_DATE BETWEEN vigencia_desde
                   AND COALESCE(vigencia_hasta, 'infinity'::timestamptz))    AS promocionesActivas,
                   (SELECT COUNT(*) FROM fin.turnos_caja WHERE estado = 'ABIERTO')
                                                                              AS cajasAbiertas
            """, nativeQuery = true)
    Object[] findResumenDashboardRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    default ReportDtos.ResumenDashboardResponse findResumenDashboard(LocalDate inicio, LocalDate fin) {
        // Spring Data envuelve una sola fila de native query en Object[1][N]; la
        // "fila" es row[0]. Si no hay filas, devuelve [] (length 0).
        Object[] result = findResumenDashboardRaw(inicio, fin);
        if (result == null || result.length == 0 || result[0] == null) {
            return new ReportDtos.ResumenDashboardResponse(
                    java.math.BigDecimal.ZERO, 0L, java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, 0L, 0L, 0L);
        }
        Object[] row = (Object[]) result[0];
        java.math.BigDecimal ventas = row[0] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[0];
        Long tickets = row[1] == null ? 0L : ((Number) row[1]).longValue();
        java.math.BigDecimal ticketPromedio = row[2] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[2];
        java.math.BigDecimal saldo = row[3] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[3];
        java.math.BigDecimal cobranza = row[4] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[4];
        java.math.BigDecimal valorInv = row[5] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[5];
        Long agotados = row[6] == null ? 0L : ((Number) row[6]).longValue();
        Long promos = row[7] == null ? 0L : ((Number) row[7]).longValue();
        Long cajas = row[8] == null ? 0L : ((Number) row[8]).longValue();
        return new ReportDtos.ResumenDashboardResponse(
                ventas, tickets, ticketPromedio, saldo, cobranza, valorInv,
                agotados, promos, cajas);
    }

    /**
     * Cierres diarios de caja (vista {@code fin.vw_cierre_diario}) acotados al
     * rango.
     */
    @Query(value = """
            SELECT fecha,
                   num_cortes AS numCortes,
                   tickets,
                   total_vendido AS totalVendido,
                   utilidad_bruta AS utilidadBruta,
                   margen_pct_promedio AS margenPctPromedio,
                   perdidas,
                   entradas_efectivo AS entradasEfectivo,
                   salidas_efectivo AS salidasEfectivo,
                   efectivo_depositado AS efectivoDepositado,
                   diferencia_total AS diferenciaTotal,
                   ingresos_digitales AS ingresosDigitales,
                   todo_cuadrado AS todoCuadrado
            FROM fin.vw_cierre_diario
            WHERE fecha BETWEEN :inicio AND :fin
            ORDER BY fecha
            """, nativeQuery = true)
    List<Tuple> findCierreDiarioRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.CierreDiarioResponse> findCierreDiario(LocalDate inicio, LocalDate fin) {
        return findCierreDiarioRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.CierreDiarioResponse(
                        // Extrae java.sql.Date nativo y convierte a LocalDate
                        tuple.get("fecha", java.sql.Date.class).toLocalDate(),
                        // Campos de conteo enteros (ajusta a .intValue() si usas Integer/int)
                        ((Number) tuple.get("numCortes")).longValue(),
                        ((Number) tuple.get("tickets")).longValue(),
                        // Campos monetarios y porcentajes mapeados a BigDecimal
                        tuple.get("totalVendido", java.math.BigDecimal.class),
                        tuple.get("utilidadBruta", java.math.BigDecimal.class),
                        tuple.get("margenPctPromedio", java.math.BigDecimal.class),
                        tuple.get("perdidas", java.math.BigDecimal.class),
                        tuple.get("entradasEfectivo", java.math.BigDecimal.class),
                        tuple.get("salidasEfectivo", java.math.BigDecimal.class),
                        tuple.get("efectivoDepositado", java.math.BigDecimal.class),
                        tuple.get("diferenciaTotal", java.math.BigDecimal.class),
                        tuple.get("ingresosDigitales", java.math.BigDecimal.class),
                        // Bandera booleana del estado del cierre
                        tuple.get("todoCuadrado", Boolean.class)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Productos sin venta reciente (vista {@code inv.vw_productos_sin_movimiento}).
     */
    @Query(value = """
            SELECT producto_id AS productoId,
                   codigo,
                   producto,
                   categoria,
                   stock,
                   costo_actual AS costoActual,
                   dinero_detenido_en_estante AS dineroDetenidoEnEstante,
                   ultima_venta AS ultimaVenta,
                   dias_sin_vender AS diasSinVender,
                   prioridad_promocion AS prioridadPromocion
            FROM inv.vw_productos_sin_movimiento
            ORDER BY dias_sin_vender DESC
            """, nativeQuery = true)
    List<Tuple> findProductosSinMovimientoRaw();

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.ProductosSinMovimientoResponse> findProductosSinMovimiento() {
        return findProductosSinMovimientoRaw().stream()
                .map(tuple -> {
                    // Control de nulos seguro para productos que nunca se han vendido
                    java.sql.Date sqlDate = tuple.get("ultimaVenta", java.sql.Date.class);
                    java.time.LocalDate ultimaVentaLocalDate = (sqlDate != null) ? sqlDate.toLocalDate() : null;

                    return new ReportDtos.ProductosSinMovimientoResponse(
                            // productoId (Casteo seguro)
                            ((Number) tuple.get("productoId")).longValue(),
                            tuple.get("codigo", String.class),
                            tuple.get("producto", String.class),
                            tuple.get("categoria", String.class),
                            // stock (Ajusta a .intValue() si tu record usa Integer)
                            ((Number) tuple.get("stock")).longValue(),
                            // Campos monetarios mapeados a BigDecimal
                            tuple.get("costoActual", java.math.BigDecimal.class),
                            tuple.get("dineroDetenidoEnEstante", java.math.BigDecimal.class),
                            // Fecha con soporte de nulos
                            ultimaVentaLocalDate,
                            // diasSinVender
                            ((Number) tuple.get("diasSinVender")).longValue(),
                            // prioridadPromocion (Ajusta el tipo si usas un Enum o número)
                            tuple.get("prioridadPromocion", String.class)
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Top 20 categorías por ingreso en el rango, con ranking por mes y
     * acumulado histórico.
     */
    @Query(value = """
            SELECT CAST(:inicio AS date) AS mes, c.categoria_id AS categoriaId,
                   c.nombre AS categoria,
                   SUM(d.cantidad)::numeric(14,2)                       AS unidadesVendidas,
                   SUM(d.total_linea)::numeric(14,2)                    AS ingreso,
                   (SUM(d.total_linea) - SUM(d.cantidad * d.costo_unitario))::numeric(14,2)
                                                                                    AS utilidad,
                   RANK() OVER (ORDER BY SUM(d.total_linea) DESC)       AS rankingMes,
                   RANK() OVER (ORDER BY SUM(d.cantidad) DESC)          AS rankingHistorico
            FROM ven.venta_detalles d
            JOIN ven.ventas v ON v.venta_id = d.venta_id AND v.estado = 'COMPLETADA'
            JOIN inv.productos p ON p.producto_id = d.producto_id
            LEFT JOIN cat.categorias c ON c.categoria_id = p.categoria_id
            WHERE v.fecha_local BETWEEN :inicio AND :fin
            GROUP BY c.categoria_id, c.nombre
            ORDER BY ingreso DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Tuple> findMejoresCategoriasRaw(
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

    // Método puente para exponer el Record limpio hacia tus servicios
    default List<ReportDtos.MejoresCategoriasResponse> findMejoresCategorias(LocalDate inicio, LocalDate fin) {
        return findMejoresCategoriasRaw(inicio, fin).stream()
                .map(tuple -> new ReportDtos.MejoresCategoriasResponse(
                        // Extrae java.sql.Date nativo y convierte a LocalDate
                        tuple.get("mes", java.sql.Date.class).toLocalDate(),
                        // c.categoria_id (Casteo numérico seguro)
                        ((Number) tuple.get("categoriaId")).longValue(),
                        tuple.get("categoria", String.class),
                        // Valores calculados en Postgres mapeados a BigDecimal
                        tuple.get("unidadesVendidas", Long.class),
                        tuple.get("ingreso", java.math.BigDecimal.class),
                        tuple.get("utilidad", java.math.BigDecimal.class),
                        // RANK() casteados a Long de forma segura
                        ((Number) tuple.get("rankingMes")).longValue(),
                        ((Number) tuple.get("rankingHistorico")).longValue()
                ))
                .collect(Collectors.toList());
    }
}