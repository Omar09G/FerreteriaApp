package mx.ferreteria.api.notif.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class RentasDtos {
    private RentasDtos() {
    }

    public record RentasEnvioResponse(
            LocalDate fecha,
            int destinatarios,
            int emailsEnviados,
            int whatsappsEnviados,
            int vencidas,
            int proximas) {
    }

    public record RentasEstadoResponse(
            LocalDate fecha,
            boolean yaEnviado,
            String estado,
            Instant enviadoEn) {
    }
}
