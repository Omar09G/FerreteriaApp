package mx.ferreteria.api.notif.dto;

import java.time.Instant;

public final class BandejaDtos {

    private BandejaDtos() {
    }

    public record BandejaResponse(
            Long bandejaId,
            String tipo,
            String titulo,
            String detalle,
            String refTipo,
            Long refId,
            Instant leidaEn,
            Instant creadaEn) {
    }

    public record NoLeidasResponse(long noLeidas) {
    }

    public record EliminadasResponse(long eliminadas) {
    }
}
