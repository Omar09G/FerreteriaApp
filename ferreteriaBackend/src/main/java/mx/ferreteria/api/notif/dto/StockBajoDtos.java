package mx.ferreteria.api.notif.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class StockBajoDtos {
    private StockBajoDtos() {
    }

    public record StockBajoEnvioResponse(
            LocalDate fecha,
            int destinatarios,
            int emailsEnviados,
            int whatsappsEnviados,
            int productos,
            int agotados,
            int almacenes) {
    }

    public record StockBajoEstadoResponse(
            LocalDate fecha,
            boolean yaEnviado,
            String estado,
            Instant enviadoEn) {
    }
}
