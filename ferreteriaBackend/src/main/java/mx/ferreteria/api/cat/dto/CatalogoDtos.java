package mx.ferreteria.api.cat.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public final class CatalogoDtos {

    private CatalogoDtos() { }

    // ── cat.estados ────────────────────────────────────────────────

    public record EstadoRequest(
            @NotBlank @Size(max = 3) String claveInegi,
            @NotBlank @Size(max = 60) String nombre) { }

    public record EstadoResponse(
            Integer estadoId,
            String claveInegi,
            String nombre) { }

    // ── cat.ciudades ───────────────────────────────────────────────

    public record CiudadRequest(
            @NotNull Integer estadoId,
            @NotBlank @Size(max = 100) String nombre) { }

    public record CiudadResponse(
            Integer ciudadId,
            Integer estadoId,
            String estadoNombre,
            String nombre) { }

    // ── cat.puestos ────────────────────────────────────────────────

    public record PuestoRequest(
            @NotBlank @Size(max = 80) String nombre,
            @DecimalMin("0") BigDecimal sueldoBase) { }

    public record PuestoResponse(
            Integer puestoId,
            String nombre,
            BigDecimal sueldoBase,
            Boolean activo) { }

    // ── cat.motivos_movimiento ─────────────────────────────────────

    public record MotivoMovimientoRequest(
            @NotBlank @Size(max = 30) String clave,
            @NotBlank @Size(max = 80) String nombre,
            @NotBlank @Pattern(regexp = "ENTRADA|SALIDA") String tipoDefault) { }

    public record MotivoMovimientoResponse(
            Integer motivoId,
            String clave,
            String nombre,
            String tipoDefault,
            Boolean activo) { }

    // ── cat.tipos_gasto ────────────────────────────────────────────

    public record TipoGastoRequest(
            @NotBlank @Size(max = 30) String clave,
            @NotBlank @Size(max = 80) String nombre,
            Boolean esFijo) { }

    public record TipoGastoResponse(
            Integer tipoGastoId,
            String clave,
            String nombre,
            Boolean esFijo,
            Boolean activo) { }

    // ── cat.formas_pago ────────────────────────────────────────────

    public record FormaPagoRequest(
            @NotBlank @Size(max = 25) String clave,
            @NotBlank @Size(max = 60) String nombre,
            Boolean esEfectivo,
            Boolean requiereReferencia,
            Boolean afectaCaja,
            String formaPagoSatClave,
            @DecimalMin("0") BigDecimal comisionPct) { }

    public record FormaPagoResponse(
            Integer formaPagoId,
            String clave,
            String nombre,
            Boolean esEfectivo,
            Boolean requiereReferencia,
            Boolean afectaCaja,
            String formaPagoSatClave,
            BigDecimal comisionPct,
            Boolean activo) { }

    // ── fis.impuestos ──────────────────────────────────────────────

    public record ImpuestoRequest(
            @NotBlank @Size(max = 5) String claveSat,
            @NotBlank @Size(max = 60) String nombre,
            @NotBlank @Pattern(regexp = "TRASLADADO|RETENIDO|LOCAL") String tipo) { }

    public record ImpuestoResponse(
            Integer impuestoId,
            String claveSat,
            String nombre,
            String tipo,
            Boolean activo) { }

    // ── fis.tasas_impuesto ─────────────────────────────────────────

    public record TasaImpuestoRequest(
            @NotNull Integer impuestoId,
            @NotNull @DecimalMin("0") BigDecimal tasa,
            @NotBlank @Pattern(regexp = "TASA|CUOTA|EXENTO") String factor,
            @NotBlank @Pattern(regexp = "VENTA|COMPRA|NOMINA") String ambito,
            Boolean zonaFrontera,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta) { }

    public record TasaImpuestoResponse(
            Integer tasaId,
            Integer impuestoId,
            String impuestoNombre,
            BigDecimal tasa,
            String factor,
            String ambito,
            Boolean zonaFrontera,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta,
            Boolean activo) { }

    // ── fis.regimenes_fiscales ─────────────────────────────────────

    public record RegimenFiscalRequest(
            @NotBlank @Size(max = 3) String claveSat,
            @NotBlank @Size(max = 120) String descripcion,
            Boolean personaFisica,
            Boolean personaMoral) { }

    public record RegimenFiscalResponse(
            String claveSat,
            String descripcion,
            Boolean personaFisica,
            Boolean personaMoral,
            Boolean activo) { }

    // ── fis.usos_cfdi ──────────────────────────────────────────────

    public record UsoCfdiRequest(
            @NotBlank @Size(max = 4) String clave,
            @NotBlank @Size(max = 150) String descripcion,
            Boolean aplicaFisica,
            Boolean aplicaMoral) { }

    public record UsoCfdiResponse(
            String clave,
            String descripcion,
            Boolean aplicaFisica,
            Boolean aplicaMoral,
            Boolean activo) { }

    // ── fis.formas_pago_sat ────────────────────────────────────────

    public record FormaPagoSatRequest(
            @NotBlank @Size(max = 2) String clave,
            @NotBlank @Size(max = 80) String descripcion) { }

    public record FormaPagoSatResponse(
            String clave,
            String descripcion,
            Boolean activo) { }

    // ── fis.metodos_pago_sat ───────────────────────────────────────

    public record MetodoPagoSatRequest(
            @NotBlank @Size(max = 3) String clave,
            @NotBlank @Size(max = 60) String descripcion) { }

    public record MetodoPagoSatResponse(
            String clave,
            String descripcion,
            Boolean activo) { }

    // ── fis.unidades_sat ───────────────────────────────────────────

    public record UnidadSatRequest(
            @NotBlank @Size(max = 4) String clave,
            @NotBlank @Size(max = 80) String descripcion) { }

    public record UnidadSatResponse(
            String clave,
            String descripcion,
            Boolean activo) { }

    // ── fis.claves_prod_serv ───────────────────────────────────────

    public record ClaveProdServRequest(
            @NotBlank @Size(max = 8) String clave,
            @NotBlank String descripcion,
            Boolean incluyeIva,
            Boolean ejemplo) { }

    public record ClaveProdServResponse(
            String clave,
            String descripcion,
            Boolean incluyeIva,
            Boolean ejemplo) { }

    // ── cfg.configuracion ──────────────────────────────────────────

    public record ConfiguracionRequest(
            @NotBlank @Size(max = 60) String clave,
            @NotBlank String valor,
            @Size(max = 200) String descripcion) { }

    public record ConfiguracionResponse(
            String clave,
            String valor,
            String descripcion) { }

    // ── cfg.folios ─────────────────────────────────────────────────

    public record FolioRequest(
            @NotBlank @Size(max = 25) String tipo,
            @NotBlank @Size(max = 6) String prefijo,
            @Min(0) Long consecutivo) { }

    public record FolioResponse(
            String tipo,
            String prefijo,
            Long consecutivo) { }
}
