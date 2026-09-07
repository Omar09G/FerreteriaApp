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
    List<CuentasPagarResponse> vwCuentasPagar();

    @Query(value = "SELECT * FROM com.vw_cuentas_pagar WHERE estado = :estado",
            nativeQuery = true)
    List<CuentasPagarResponse> vwCuentasPagarPorEstado(@Param("estado") String estado);

    @Query(value = "SELECT * FROM com.vw_facturas_vencidas", nativeQuery = true)
    List<FacturaVencidaResponse> vwFacturasVencidas();

    @Query(value = "SELECT * FROM com.vw_facturas_pendientes", nativeQuery = true)
    List<FacturaPendienteResponse> vwFacturasPendientes();

    @Query(value = "SELECT * FROM com.vw_ultimas_facturas_proveedor "
            + "WHERE proveedor_id = :proveedorId", nativeQuery = true)
    List<FacturaProveedorResponse> vwUltimasFacturasProveedor(
            @Param("proveedorId") Integer proveedorId);

    /**
     * Contexto de una cuenta por pagar (cabecera cp JOIN compras para folio y
     * almacén). Usado en la lectura inicial y relectura tras el abono.
     */
    @Query(value = """
            SELECT cp.cuenta_pagar_id, c.folio AS compra_folio, cp.estado,
                   cp.monto_total, cp.monto_pagado,
                   cp.monto_total - cp.monto_pagado AS saldo, c.almacen_id
            FROM com.cuentas_pagar cp
            JOIN com.compras c ON c.compra_id = cp.compra_id
            WHERE cp.cuenta_pagar_id = :cuentaPagarId
            """, nativeQuery = true)
    List<CuentaPagoDetalle> findCuentaPagoDetalle(@Param("cuentaPagarId") Long cuentaPagarId);

    @Modifying
    @Query(value = """
            INSERT INTO com.pagos_proveedor
                (cuenta_pagar_id, forma_pago_id, referencia, monto, usuario_id, turno_caja_id)
            VALUES (:cuentaPagarId, :formaPagoId, :referencia, :monto, :usuarioId, :turnoCajaId)
            RETURNING pago_proveedor_id
            """, nativeQuery = true)
    Long insertPagoProveedor(@Param("cuentaPagarId") Long cuentaPagarId,
            @Param("formaPagoId") Integer formaPagoId,
            @Param("referencia") String referencia,
            @Param("monto") BigDecimal monto,
            @Param("usuarioId") Integer usuarioId,
            @Param("turnoCajaId") Long turnoCajaId);
}