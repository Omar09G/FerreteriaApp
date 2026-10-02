package mx.ferreteria.api.notif.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class InformeDtos {
    private InformeDtos() {
    }

    public record InformeEnvioResponse(
                    LocalDate fechaInicio, LocalDate fechaFin,
                    int destinatarios, int emailsEnviados, int whatsappEnviados) {
    }

    public record InformeEstadoResponse(
                    LocalDate fechaInicio, LocalDate fechaFin,
                    boolean yaEnviado, String estado, Instant enviadoEn) {
    }
}
