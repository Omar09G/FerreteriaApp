package mx.ferreteria.api.notif.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class TurnoAbiertoDtos {
    private TurnoAbiertoDtos() {
    }

    public record TurnoAbiertoEnvioResponse(
            LocalDate fecha,
            int destinatarios,
            int emailsEnviados,
            int whatsappsEnviados,
            int turnos) {
    }

    public record TurnoAbiertoEstadoResponse(
            LocalDate fecha,
            boolean yaEnviado,
            String estado,
            Instant enviadoEn) {
    }
}
