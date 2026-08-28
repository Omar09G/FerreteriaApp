package mx.ferreteria.api.rh.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** DTOs del módulo rh (nómina básica). */
public final class RhDtos {
    private RhDtos() {
    }

    public record NominaRequest(
            @NotNull Integer empleadoId,
            @NotNull LocalDate periodoIni,
            @NotNull LocalDate periodoFin,
            @NotNull @DecimalMin("0.1") BigDecimal diasPagados,
            @NotNull @DecimalMin("0") BigDecimal percepciones,
            @NotNull @DecimalMin("0") BigDecimal deducciones,
            String notas) {
    }

    public record NominaResponse(
            Long nominaId, Integer empleadoId, String empleado,
            LocalDate periodoIni, LocalDate periodoFin,
            BigDecimal diasPagados,
            BigDecimal percepciones, BigDecimal deducciones,
            BigDecimal netoPagar, String estado, Instant fechaPago,
            Integer usuarioRegistraId, String notas) {
    }
}