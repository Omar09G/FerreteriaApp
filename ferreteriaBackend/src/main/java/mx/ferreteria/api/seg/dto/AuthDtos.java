package mx.ferreteria.api.seg.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;

/** Contratos del API de autenticación (M1). Mensajes: solo ErrorCode, nunca texto. */
public final class AuthDtos {

    private AuthDtos() { }

    public record LoginRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 100) String password) { }

    public record RegisterRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 80) String nombre,
            @NotBlank @Size(max = 80) String apellidoPaterno,
            @Size(max = 80) String apellidoMaterno,
            @Size(max = 20) String telefono,
            @jakarta.validation.constraints.NotNull Integer puestoId) { }

    public record RegisterResponse(
            int usuarioId,
            Integer empleadoId,
            String username,
            String email) { }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 100) String passwordActual,
            @NotBlank @Size(min = 8, max = 100) String nuevaPassword) { }

    public record PasswordOk(boolean cambiada) { }

    public record RefreshRequest(
            // Opcional: el refresh puede viajar en la cookie HttpOnly `rt` cuando
            // el cliente es un browser con withCredentials. Mantener el campo
            // permite compat con clientes no-browser (Postman, curl, tests).
            String refreshToken) { }

    public record TokenResponse(
            String accessToken,
            // refreshToken ya NO se envía en el body cuando viaja en cookie
            // HttpOnly. Queda null en ese caso para no duplicar material
            // sensible fuera del Set-Cookie.
            String refreshToken,
            long expiresInSeconds,
            MeResponse usuario) { }

    public record MeResponse(
            int usuarioId,
            String username,
            Integer empleadoId,
            List<String> roles,
            String ultimoLogin,
            EmpleadoResumen empleado) { }

    public record LogoutOk(boolean revocado) { }
}
