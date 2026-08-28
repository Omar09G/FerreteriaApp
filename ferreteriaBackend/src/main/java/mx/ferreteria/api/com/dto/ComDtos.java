package mx.ferreteria.api.com.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** DTOs del módulo com (compras, cuentas por pagar y reportes de proveedor). */
public final class ComDtos {
    private ComDtos() {
    }

    // ─── Compra ────────────────────────────────────────────────────
    public record CompraRequest(
            @NotNull Integer proveedorId,
            @NotNull Integer almacenId,
            @NotNull Integer formaPagoId,
            String facturaProveedor,
            Long ordenCompraId,
            Long turnoCajaId,
            String notas,
            @Valid @NotNull List<CompraDetalleRequest> detalles) {
    }

    public record CompraDetalleRequest(
            @NotNull Long productoId,
            @NotNull @DecimalMin("0.001") BigDecimal cantidad,
            @NotNull @DecimalMin("0") BigDecimal costoUnitario) {
    }

    public record CompraDetalleResponse(
            Long compraDetalleId, Long productoId, String producto,
            BigDecimal cantidad, BigDecimal costoUnitario,
            BigDecimal importeLinea) {
    }

    public record CompraResponse(
            Long compraId, String folio, String facturaProveedor,
            Integer proveedorId, String proveedor,
            Integer almacenId, String almacen,
            Instant fecha, Integer formaPagoId, String formaPago,
            BigDecimal subtotal, BigDecimal iva,
            BigDecimal descuentoTotal, BigDecimal total,
            String estado, Integer usuarioId, Long turnoCajaId,
            String notas, List<CompraDetalleResponse> detalles) {
    }

    // ─── Cuentas por pagar (com.vw_cuentas_pagar) ───────────────────
    public record CuentasPagarResponse(
            Long cuentaPagarId, String compraFolio, String proveedor,
            BigDecimal montoTotal, BigDecimal montoPagado, BigDecimal saldo,
            LocalDate fechaVencimiento, Integer diasVencido, String estado) {
    }

    // ─── Facturas del proveedor (com.vw_ultimas_facturas_proveedor) ─
    public record FacturaProveedorResponse(
            Integer numeroMasReciente, Integer proveedorId, String proveedor,
            String compraFolio, String facturaProveedor, LocalDate fecha,
            BigDecimal subtotal, BigDecimal iva, BigDecimal total,
            BigDecimal montoTotal, BigDecimal montoPagado, BigDecimal saldo,
            String estadoPago, LocalDate fechaVencimiento) {
    }

    // ─── Vencidas (com.vw_facturas_vencidas) ────────────────────────
    public record FacturaVencidaResponse(
            Long cuentaPagarId, String compraFolio, String facturaProveedor,
            Integer proveedorId, String proveedor, String contactoTelefono,
            LocalDate fechaCompra, BigDecimal montoTotal, BigDecimal montoPagado,
            BigDecimal saldo, LocalDate fechaVencimiento,
            Integer diasVencido, String antiguedad) {
    }

    // ─── Pendientes (com.vw_facturas_pendientes) ────────────────────
    public record FacturaPendienteResponse(
            Long cuentaPagarId, String compraFolio, String facturaProveedor,
            Integer proveedorId, String proveedor, LocalDate fechaCompra,
            BigDecimal montoTotal, BigDecimal montoPagado, BigDecimal saldo,
            String estadoPago, LocalDate fechaVencimiento,
            Integer diasParaVencer, String alerta) {
    }
}