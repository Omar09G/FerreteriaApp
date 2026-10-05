package mx.ferreteria.api.notif.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class CuentasPagarDtos {
    private CuentasPagarDtos() {
    }

    public record CuentasPagarEnvioResponse(
            LocalDate fecha,
            int destinatarios,
            int emailsEnviados,
            int vencidas,
            int pendientes,
            BigDecimal totalVencido,
            BigDecimal totalPendiente) {
    }

    public record CuentasPagarEstadoResponse(
            LocalDate fecha,
            boolean yaEnviado,
            String estado,
            Instant enviadoEn) {
    }
}
