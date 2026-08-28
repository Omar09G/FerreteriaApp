package mx.ferreteria.api.fis.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** DTOs del módulo fis (CFDI persistido para consulta; timbrado PAC es futuro). */
public final class FisDtos {
    private FisDtos() {
    }

    public record FacturaFisRequest(
            @NotBlank @Pattern(regexp = "^(EMITIDA|RECIBIDA)$") String tipo,
            String serie,
            @NotBlank String folio,
            String uuid,
            @NotBlank @Pattern(regexp = "^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$") String emisorRfc,
            @NotBlank @Pattern(regexp = "^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$") String receptorRfc,
            @NotNull @DecimalMin("0") BigDecimal subtotal,
            @NotNull @DecimalMin("0") BigDecimal iva,
            String cfdiXml,
            Long ventaId) {
    }

    public record FacturaFisResponse(
            Long facturaId, String tipo, String serie, String folio,
            String uuid, String emisorRfc, String receptorRfc,
            BigDecimal subtotal, BigDecimal iva, BigDecimal total,
            Instant fechaTimbrado, String estado, Long ventaId,
            Integer usuarioId, Instant creadoEn) {
    }

    public record FacturaXmlResponse(
            Long facturaId, String folio, String uuid,
            String tipo, String cfdiXml) {
    }
}