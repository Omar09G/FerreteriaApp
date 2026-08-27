package mx.ferreteria.api.seg.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos del API de autenticación (M1). Mensajes: solo ErrorCode, nunca texto. */
public final class AuthDtos {

    private AuthDtos() { }

    public record LoginRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 100) String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            MeResponse usuario) { }

    public record MeResponse(
            int usuarioId,
            String username,
            Integer empleadoId,
            List<String> roles,
            String ultimoLogin) { }

    public record LogoutOk(boolean revocado) { }
}
