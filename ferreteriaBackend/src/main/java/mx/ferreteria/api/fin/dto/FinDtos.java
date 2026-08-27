package mx.ferreteria.api.fin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public final class FinDtos {
    private FinDtos() {
    }

    // ─── Caja ───────────────────────────────────────────────────────
    public record CajaResponse(
            Integer cajaId, String nombre, Integer almacenId,
            String almacenNombre, Boolean activa) {
    }

    // ─── Turno ──────────────────────────────────────────────────────
    public record TurnoCajaResponse(
            Long turnoCajaId, Integer cajaId, String cajaNombre,
            Integer usuarioId, Instant aperturaEn, BigDecimal montoApertura,
            Instant cierreEn, BigDecimal montoEsperado, BigDecimal montoContado,
            BigDecimal diferencia, String estado, String observaciones) {
    }

    public record TurnoAperturaRequest(
            @NotNull Integer cajaId,
            @NotNull @Min(0) BigDecimal montoApertura) {
    }

    public record TurnoCierreRequest(
            @NotNull BigDecimal montoContado,
            String observaciones) {
    }

    // ─── Movimiento ─────────────────────────────────────────────────
    public record MovimientoCajaResponse(
            Long movimientoId, Long turnoCajaId, String tipo,
            String concepto, BigDecimal monto,
            Integer formaPagoId, String formaPagoNombre,
            String refTabla, Long refId, Instant creadoEn) {
    }

    public record MovimientoCajaRequest(
            @NotBlank String tipo,
            @NotBlank String concepto,
            @NotNull @Min(1) BigDecimal monto,
            Integer formaPagoId,
            String refTabla,
            Long refId) {
    }

    // ─── Corte ──────────────────────────────────────────────────────
    public record CorteCajaResponse(
            Long corteId, Long turnoCajaId,
            Integer cajaId, String cajaNombre,
            Integer almacenId, String almacenNombre,
            Integer usuarioId, Integer usuarioCierreId,
            LocalDate fecha, Instant aperturaEn, Instant cierreEn,
            Long numVentas,
            BigDecimal subtotal, BigDecimal iva, BigDecimal descuentos,
            BigDecimal totalVendido, BigDecimal costoVentas,
            BigDecimal utilidadBruta, BigDecimal margenPct,
            BigDecimal fondoApertura,
            BigDecimal entradasEfectivo, BigDecimal salidasEfectivo,
            BigDecimal dineroEsperado, BigDecimal dineroContado,
            BigDecimal diferencia, String resultadoCaja,
            BigDecimal ingresosNoEfectivo, BigDecimal egresosNoEfectivo,
            BigDecimal perdidasInventario,
            String desgloseEntradas, String desgloseSalidas,
            String desgloseFormasPago, String observaciones) {
    }

    public record CorteRequest(
            @NotNull BigDecimal montoContado,
            String observaciones) {
    }

    // ─── Gasto ──────────────────────────────────────────────────────
    public record GastoRequest(
            @NotNull Integer tipoGastoId,
            @NotBlank String descripcion,
            @NotNull @Min(1) BigDecimal monto,
            LocalDate fechaGasto,
            @NotNull Integer formaPagoId,
            Integer proveedorId,
            Long turnoCajaId,
            String facturaUuid) {
    }

    public record GastoResponse(
            Long gastoId, String folio,
            Integer tipoGastoId, String tipoGastoNombre,
            String descripcion, BigDecimal monto,
            LocalDate fechaGasto,
            Integer formaPagoId, String formaPagoNombre,
            Integer proveedorId, Long turnoCajaId,
            String facturaUuid, Integer usuarioId, Instant creadoEn) {
    }

    // ─── IngresoOtro ────────────────────────────────────────────────
    public record IngresoOtroRequest(
            @NotBlank String concepto,
            @NotNull @Min(1) BigDecimal monto,
            LocalDate fecha,
            @NotNull Integer formaPagoId,
            Long turnoCajaId) {
    }

    public record IngresoOtroResponse(
            Long ingresoOtroId, String concepto,
            BigDecimal monto, LocalDate fecha,
            Integer formaPagoId, String formaPagoNombre,
            Long turnoCajaId, Integer usuarioId, Instant creadoEn) {
    }
}
