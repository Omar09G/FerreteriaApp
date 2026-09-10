package mx.ferreteria.api.com.repo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle;
import mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaProveedorResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse;
import mx.ferreteria.api.com.entity.Compra;

/**
 * Consultas nativas para los reportes del módulo com (vistas com.vw_*) y el
 * INSERT a com.pagos_proveedor con RETURNING. Reemplaza al uso de JdbcTemplate
 * en CompraService para mantener todo el acceso a BD dentro de repositorios.
 *
 * Se extiende JpaRepository&lt;Compra, Long&gt; únicamente para que Spring Data
 * detecte la interfaz como repositorio; los métodos CRUD heredados no se usan.
 */
public interface CompraReportRepository extends JpaRepository<Compra, Long> {

    @Query(value = "SELECT * FROM com.vw_cuentas_pagar", nativeQuery = true)
    List<Object[]> vwCuentasPagarRaw();

    @Query(value = "SELECT * FROM com.vw_cuentas_pagar WHERE estado = :estado",
            nativeQuery = true)
    List<Object[]> vwCuentasPagarPorEstadoRaw(@Param("estado") String estado);

    @Query(value = "SELECT * FROM com.vw_facturas_vencidas", nativeQuery = true)
    List<Object[]> vwFacturasVencidasRaw();

    @Query(value = "SELECT * FROM com.vw_facturas_pendientes", nativeQuery = true)
    List<Object[]> vwFacturasPendientesRaw();

    @Query(value = "SELECT * FROM com.vw_ultimas_facturas_proveedor "
            + "WHERE proveedor_id = :proveedorId", nativeQuery = true)
    List<Object[]> vwUltimasFacturasProveedorRaw(
            @Param("proveedorId") Integer proveedorId);

    @Query(value = """
            SELECT cp.cuenta_pagar_id, c.folio, cp.estado,
                   cp.monto_total, cp.monto_pagado,
                   cp.monto_total - cp.monto_pagado, c.almacen_id
            FROM com.cuentas_pagar cp
            JOIN com.compras c ON c.compra_id = cp.compra_id
            WHERE cp.cuenta_pagar_id = :cuentaPagarId
            """, nativeQuery = true)
    List<Object[]> findCuentaPagoDetalleRaw(@Param("cuentaPagarId") Long cuentaPagarId);

    default List<CuentaPagoDetalle> findCuentaPagoDetalle(Long cuentaPagarId) {
        return findCuentaPagoDetalleRaw(cuentaPagarId).stream().map(r -> new CuentaPagoDetalle(
                toLong(r[0]), toString(r[1]), toString(r[2]),
                toBigDecimal(r[3]), toBigDecimal(r[4]), toBigDecimal(r[5]),
                toInteger(r[6]))).toList();
    }

    // ─── Helpers de conversión para las proyecciones nativas ────────────
    private static Long toLong(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static Integer toInteger(Object o) { return o == null ? null : ((Number) o).intValue(); }
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }
    private static String toString(Object o) { return o == null ? null : o.toString(); }
    private static java.time.LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.time.LocalDate ld) return ld;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return java.time.LocalDate.parse(o.toString());
    }

    default List<CuentasPagarResponse> vwCuentasPagar() {
        return vwCuentasPagarRaw().stream().map(r -> new CuentasPagarResponse(
                toLong(r[0]), toString(r[1]), toString(r[2]),
                toBigDecimal(r[3]), toBigDecimal(r[4]), toBigDecimal(r[5]),
                toLocalDate(r[6]), toInteger(r[7]), toString(r[8]))).toList();
    }

    default List<CuentasPagarResponse> vwCuentasPagarPorEstado(String estado) {
        return vwCuentasPagarPorEstadoRaw(estado).stream().map(r -> new CuentasPagarResponse(
                toLong(r[0]), toString(r[1]), toString(r[2]),
                toBigDecimal(r[3]), toBigDecimal(r[4]), toBigDecimal(r[5]),
                toLocalDate(r[6]), toInteger(r[7]), toString(r[8]))).toList();
    }

    default List<FacturaVencidaResponse> vwFacturasVencidas() {
        return vwFacturasVencidasRaw().stream().map(r -> new FacturaVencidaResponse(
                toLong(r[0]), toString(r[1]), toString(r[2]),
                toInteger(r[3]), toString(r[4]), toString(r[5]),
                toLocalDate(r[6]), toBigDecimal(r[7]), toBigDecimal(r[8]), toBigDecimal(r[9]),
                toLocalDate(r[10]), toInteger(r[11]), toString(r[12]))).toList();
    }

    default List<FacturaPendienteResponse> vwFacturasPendientes() {
        return vwFacturasPendientesRaw().stream().map(r -> new FacturaPendienteResponse(
                toLong(r[0]), toString(r[1]), toString(r[2]),
                toInteger(r[3]), toString(r[4]), toLocalDate(r[5]),
                toBigDecimal(r[6]), toBigDecimal(r[7]), toBigDecimal(r[8]),
                toString(r[9]), toLocalDate(r[10]), toInteger(r[11]), toString(r[12]))).toList();
    }

    default List<FacturaProveedorResponse> vwUltimasFacturasProveedor(Integer proveedorId) {
        return vwUltimasFacturasProveedorRaw(proveedorId).stream().map(r -> new FacturaProveedorResponse(
                toInteger(r[0]), toInteger(r[1]), toString(r[2]),
                toString(r[3]), toString(r[4]), toLocalDate(r[5]),
                toBigDecimal(r[6]), toBigDecimal(r[7]), toBigDecimal(r[8]),
                toBigDecimal(r[9]), toBigDecimal(r[10]), toBigDecimal(r[11]),
                toString(r[12]), toLocalDate(r[13]))).toList();
    }

    @Modifying
    @Query(value = """
            INSERT INTO com.pagos_proveedor
                (cuenta_pagar_id, forma_pago_id, referencia, monto, usuario_id, turno_caja_id)
            VALUES (:cuentaPagarId, :formaPagoId, :referencia, :monto, :usuarioId, :turnoCajaId)
            """, nativeQuery = true)
    int insertPagoProveedor(@Param("cuentaPagarId") Long cuentaPagarId,
            @Param("formaPagoId") Integer formaPagoId,
            @Param("referencia") String referencia,
            @Param("monto") BigDecimal monto,
            @Param("usuarioId") Integer usuarioId,
            @Param("turnoCajaId") Long turnoCajaId);
}