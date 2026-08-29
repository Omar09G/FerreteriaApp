package mx.ferreteria.api.ven.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class VenDtos {
    private VenDtos() {}

    // ─── Cotización ─────────────────────────────────────────────────
    public record CotizacionRequest(
        Long clienteId,
        LocalDate vigenciaHasta,
        @NotNull List<CotizacionDetalleRequest> detalles
    ) {}
    public record CotizacionDetalleRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) BigDecimal cantidad,
        @NotNull BigDecimal precioUnitario
    ) {}
    public record CotizacionResponse(
        Long cotizacionId, String folio,
        Long clienteId, String clienteNombre,
        Instant fecha, LocalDate vigenciaHasta,
        BigDecimal subtotal, BigDecimal iva, BigDecimal total,
        String estado, Long ventaGeneradaId,
        Integer usuarioId,
        List<CotizacionDetalleResponse> detalles
    ) {}
    public record CotizacionDetalleResponse(
        Long productoId, String productoNombre,
        BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal importeLinea
    ) {}

    // ─── Venta (checkout) ───────────────────────────────────────────
    public record VentaRequest(
        @NotNull Integer almacenId,
        Integer cajaId,
        Long clienteId,
        Long cotizacionId,
        @NotNull Integer formaPagoId,
        @NotNull List<VentaDetalleRequest> detalles,
        @NotNull List<PagoRequest> pagos,
        String notas
    ) {}
    public record VentaDetalleRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) BigDecimal cantidad,
        @NotNull BigDecimal precioUnitario
    ) {}
    public record PagoRequest(
        @NotNull Integer formaPagoId,
        @NotNull @Min(0) BigDecimal monto,
        String referencia
    ) {}
    public record VentaResponse(
        Long ventaId, String folio,
        Long clienteId, String clienteNombre,
        Integer almacenId, String almacenNombre,
        Instant fecha, LocalDate fechaLocal,
        Integer formaPagoId, String formaPagoNombre,
        BigDecimal ivaTasa, Boolean ivaIncluido,
        BigDecimal subtotal, BigDecimal iva,
        BigDecimal descuentoTotal, BigDecimal total,
        String estado, Integer usuarioId, Long turnoCajaId,
        String notas,
        List<VentaDetalleResponse> detalles,
        List<PagoResponse> pagos
    ) {}
    public record VentaDetalleResponse(
        Long ventaDetalleId, Long productoId, String productoNombre,
        BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal costoUnitario, BigDecimal descuentoLinea,
        BigDecimal totalLinea
    ) {}
    public record PagoResponse(
        Long pagoClienteId, Integer formaPagoId,
        String referencia, BigDecimal monto, Instant fecha
    ) {}
    public record VentaCancelRequest(
        @NotBlank String motivo
    ) {}

    // ─── Devolución ─────────────────────────────────────────────────
    public record DevolucionRequest(
        @NotNull Long ventaId,
        @NotBlank String motivo,
        @NotNull Integer formaDevolucionId,
        @NotNull List<DevolucionDetalleRequest> detalles
    ) {}
    public record DevolucionDetalleRequest(
        @NotNull Long productoId,
        Long ventaDetalleId,
        @NotNull @Min(1) BigDecimal cantidad,
        @NotNull BigDecimal precioUnitario
    ) {}
    public record DevolucionResponse(
        Long devolucionId, String folio,
        Long ventaId, String ventaFolio,
        Instant fecha, String motivo,
        BigDecimal total,
        Integer formaDevolucionId, String formaDevolucionNombre,
        Integer usuarioId,
        List<DevolucionDetalleResponse> detalles
    ) {}
    public record DevolucionDetalleResponse(
        Long productoId, String productoNombre,
        Long ventaDetalleId,
        BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal importeLinea
    ) {}

    // ─── Renta ──────────────────────────────────────────────────────
    public record RentaRequest(
        @NotNull Long clienteId,
        @NotNull Integer almacenId,
        Integer cajaId,
        @NotNull Integer formaPagoId,
        @NotNull LocalDate fechaDevEsperada,
        @NotNull @Min(0) BigDecimal deposito,
        @NotNull List<RentaDetalleRequest> detalles
    ) {}
    public record RentaDetalleRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) BigDecimal cantidad,
        @NotNull BigDecimal costoDia
    ) {}
    public record RentaResponse(
        Long rentaId, String folio,
        Long clienteId, String clienteNombre,
        Integer almacenId, String almacenNombre,
        Instant fechaRenta, LocalDate fechaDevEsperada,
        Instant fechaDevReal,
        BigDecimal deposito, BigDecimal costoTotal,
        Integer formaPagoId, Long turnoCajaId,
        String estado, Integer usuarioId,
        List<RentaDetalleResponse> detalles
    ) {}
    public record RentaDetalleResponse(
        Long productoId, String productoNombre,
        BigDecimal cantidad, BigDecimal costoDia,
        BigDecimal diasCobrados, BigDecimal subtotal
    ) {}
    public record RentaDevolucionRequest(
        @NotNull List<RentaDevolucionDetalleRequest> detalles
    ) {}
    public record RentaDevolucionDetalleRequest(
        @NotNull Long productoId,
        @NotNull BigDecimal diasCobrados
    ) {}

    // ─── Crédito / Cobranza ─────────────────────────────────────────
    public record CuentaCobrarResponse(
        Long cuentaCobrarId, Long ventaId, String ventaFolio,
        Long clienteId, String clienteNombre,
        BigDecimal montoTotal, BigDecimal montoPagado,
        BigDecimal saldo, LocalDate fechaVencimiento,
        String estado, Instant creadoEn,
        List<PagoResponse> pagos
    ) {}
    public record LineaCreditoResponse(
        Long lineaCreditoId, Long clienteId,
        BigDecimal montoAutorizado, BigDecimal montoUsado,
        BigDecimal montoDisponible,
        Short diasCredito, BigDecimal tasaMoratorio,
        String estado, LocalDate vigenteHasta
    ) {}

    // ─── Pago ───────────────────────────────────────────────────────
    public record PagoClienteRequest(
        @NotNull Long cuentaCobrarId,
        @NotNull Integer formaPagoId,
        @NotNull @Min(1) BigDecimal monto,
        String referencia,
        Long turnoCajaId
    ) {}

    // ─── Caja / Turno ───────────────────────────────────────────────
    public record CajaResponse(
        Integer cajaId, String nombre,
        Integer almacenId, String almacenNombre,
        Boolean activa
    ) {}
    public record TurnoCajaResponse(
        Long turnoCajaId, Integer cajaId, String cajaNombre,
        Integer usuarioId, Instant aperturaEn,
        BigDecimal montoApertura,
        Instant cierreEn, BigDecimal montoEsperado,
        BigDecimal montoContado, BigDecimal diferencia,
        String estado, String observaciones
    ) {}
    public record TurnoAperturaRequest(
        @NotNull Integer cajaId,
        @NotNull @Min(0) BigDecimal montoApertura
    ) {}
    public record TurnoCierreRequest(
        @NotNull BigDecimal montoContado,
        String observaciones
    ) {}

    // ─── Promociones ─────────────────────────────────────────────────
    /**
     * Cuerpo de alta/edición de una promoción. productos/categorias pueden ir
     * vacíos si la promoción aplica a "todos" o si solo usa los triggers
     * por categoría. diasSemana en [1..7]; vacío = sin restricciones.
     */
    public record PromocionRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 1000) String descripcion,
        @NotBlank String tipo,
        BigDecimal valorPct,
        BigDecimal valorMonto,
        BigDecimal precioEspecial,
        BigDecimal compraMinTotal,
        BigDecimal compraMinCantidad,
        BigDecimal lleva,
        BigDecimal paga,
        Integer maxUsosTotal,
        Integer maxUsosCliente,
        Instant vigenciaDesde,
        Instant vigenciaHasta,
        List<Short> diasSemana,
        LocalTime horaDesde,
        LocalTime horaHasta,
        Boolean soloMayoristas,
        String estado,
        List<Long> productos,
        List<Integer> categorias
    ) {}

    public record PromocionResponse(
        Long promocionId,
        String nombre,
        String descripcion,
        String tipo,
        BigDecimal valorPct,
        BigDecimal valorMonto,
        BigDecimal precioEspecial,
        BigDecimal compraMinTotal,
        BigDecimal compraMinCantidad,
        BigDecimal lleva,
        BigDecimal paga,
        Integer maxUsosTotal,
        Integer maxUsosCliente,
        Integer usosActual,
        Instant vigenciaDesde,
        Instant vigenciaHasta,
        List<Short> diasSemana,
        LocalTime horaDesde,
        LocalTime horaHasta,
        Boolean soloMayoristas,
        String estado,
        List<Long> productos,
        List<Integer> categorias,
        Integer usuarioId,
        Instant creadoEn
    ) {}
}
