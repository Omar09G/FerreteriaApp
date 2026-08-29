package mx.ferreteria.api.seg.dto;

import java.time.Instant;

public final class AuditoriaDtos {
    private AuditoriaDtos() {}

    public record AuditoriaResponse(
        Long auditoriaId,
        String esquema,
        String tabla,
        Long registroId,
        String accion,
        String datosAnteriores,
        String datosNuevos,
        Integer usuarioId,
        String usuario,
        Instant creadoEn
    ) {}

    public record TablaAuditoriaResponse(String esquema, String tabla) {}
}