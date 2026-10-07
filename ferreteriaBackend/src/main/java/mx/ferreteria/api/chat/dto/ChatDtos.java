package mx.ferreteria.api.chat.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CrearDirectaRequest(@NotNull Integer otroUsuarioId) {
    }

    public record CrearGrupoRequest(
            @NotBlank @Size(max = 120) String titulo,
            @NotNull @Size(min = 1, max = 50) List<Integer> miembroIds) {
    }

    public record EnviarMensajeRequest(@NotBlank @Size(max = 2000) String cuerpo) {
    }

    public record ParticipanteResponse(Integer usuarioId, String username) {
    }

    public record MensajeResponse(
            Long mensajeId,
            Integer autorId,
            String autorNombre,
            String cuerpo,
            Instant creadaEn) {
    }

    public record UltimoMensajeResponse(
            String cuerpo,
            String autorNombre,
            Instant creadaEn) {
    }

    public record ConversacionResponse(
            Long conversacionId,
            String tipo,
            String titulo,
            List<ParticipanteResponse> participantes,
            UltimoMensajeResponse ultimoMensaje,
            long noLeidos) {
    }
}
