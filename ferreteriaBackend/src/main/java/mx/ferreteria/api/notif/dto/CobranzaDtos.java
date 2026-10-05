package mx.ferreteria.api.notif.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class CobranzaDtos {
    private CobranzaDtos() {
    }

    public record CobranzaEnvioResponse(
            LocalDate fecha,
            int destinatarios,
            int emailsEnviados,
            int whatsappsEnviados,
            int vencidas,
            int pendientes,
            BigDecimal totalVencido,
            BigDecimal totalPendiente) {
    }

    public record CobranzaEstadoResponse(
            LocalDate fecha,
            boolean yaEnviado,
            String estado,
            Instant enviadoEn) {
    }
}
