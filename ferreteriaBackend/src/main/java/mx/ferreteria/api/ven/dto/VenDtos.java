package mx.ferreteria.api.ven.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class VenDtos {
    private VenDtos() {}

    // ─── Cotización ─────────────────────────────────────────────────
    public record CotizacionRequest(
        @Positive Long clienteId,
        LocalDate vigenciaHasta,
        @NotNull @NotEmpty @Valid List<CotizacionDetalleRequest> detalles
    ) {}
    public record CotizacionDetalleRequest(
        @NotNull @Positive Long productoId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal cantidad,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal precioUnitario
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
        @NotNull @Positive Integer almacenId,
        @Positive Integer cajaId,
        @Positive Long clienteId,
        @Positive Long cotizacionId,
        @NotNull @Positive Integer formaPagoId,
        @NotNull @NotEmpty @Valid List<VentaDetalleRequest> detalles,
        @NotNull @NotEmpty @Valid List<PagoRequest> pagos,
        @Size(max = 500) String notas
    ) {}
    public record VentaDetalleRequest(
        @NotNull @Positive Long productoId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal cantidad,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal precioUnitario
    ) {}
    public record PagoRequest(
        @NotNull @Positive Integer formaPagoId,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal monto,
        @Size(max = 80) String referencia
    ) {}
    public record ClienteVentaInfo(
        Long clienteId, String razonSocial, String nombreComercial,
        String rfc, String curp, String regimenFiscal,
        String telefono, String whatsapp, String email,
        String calle, String colonia, String cp, String ciudadNombre
    ) {}

    public record VentaResponse(
        Long ventaId, String folio,
        Long clienteId, String clienteNombre,
        ClienteVentaInfo cliente,
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
        @NotBlank @Size(max = 500) String motivo
    ) {}

    // ─── Devolución ─────────────────────────────────────────────────
    public record DevolucionRequest(
        @NotNull @Positive Long ventaId,
        @NotBlank @Size(max = 500) String motivo,
        @NotNull @Positive Integer formaDevolucionId,
        @NotNull @NotEmpty @Valid List<DevolucionDetalleRequest> detalles
    ) {}
    public record DevolucionDetalleRequest(
        @NotNull @Positive Long productoId,
        @Positive Long ventaDetalleId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal cantidad,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal precioUnitario
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
        @NotNull @Positive Long clienteId,
        @NotNull @Positive Integer almacenId,
        @Positive Integer cajaId,
        @NotNull @Positive Integer formaPagoId,
        @NotNull LocalDate fechaDevEsperada,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal deposito,
        @NotNull @NotEmpty @Valid List<RentaDetalleRequest> detalles
    ) {}
    public record RentaDetalleRequest(
        @NotNull @Positive Long productoId,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal cantidad,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal costoDia
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
        @NotNull @NotEmpty @Valid List<RentaDevolucionDetalleRequest> detalles
    ) {}
    public record RentaDevolucionDetalleRequest(
        @NotNull @Positive Long productoId,
        @NotNull @DecimalMin(value = "0.1", inclusive = true) BigDecimal diasCobrados
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
        @NotNull @Positive Long cuentaCobrarId,
        @NotNull @Positive Integer formaPagoId,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal monto,
        @Size(max = 80) String referencia,
        @Positive Long turnoCajaId
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
        @NotNull @Positive Integer cajaId,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal montoApertura
    ) {}
    public record TurnoCierreRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal montoContado,
        @Size(max = 500) String observaciones
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
        @NotBlank @Pattern(regexp = "DESCUENTO_PRODUCTO|DESCUENTO_TOTAL_VENTA|POR_CANTIDAD|NXM|PRECIO_ESPECIAL") String tipo,
        @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "100", inclusive = true) BigDecimal valorPct,
        @DecimalMin(value = "0", inclusive = true) BigDecimal valorMonto,
        @DecimalMin(value = "0", inclusive = true) BigDecimal precioEspecial,
        @DecimalMin(value = "0", inclusive = true) BigDecimal compraMinTotal,
        @DecimalMin(value = "0", inclusive = true) BigDecimal compraMinCantidad,
        @DecimalMin(value = "0.001", inclusive = true) BigDecimal lleva,
        @DecimalMin(value = "0.001", inclusive = true) BigDecimal paga,
        @Min(0) Integer maxUsosTotal,
        @Min(0) Integer maxUsosCliente,
        Instant vigenciaDesde,
        Instant vigenciaHasta,
        List<Short> diasSemana,
        LocalTime horaDesde,
        LocalTime horaHasta,
        Boolean soloMayoristas,
        @Pattern(regexp = "ACTIVA|PROGRAMADA|FINALIZADA|CANCELADA") @Size(max = 12) String estado,
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
