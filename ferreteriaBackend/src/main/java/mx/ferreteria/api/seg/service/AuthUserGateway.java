package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de identidad desacoplado del almacenamiento (testeable sin BD). */
public interface AuthUserGateway {

    record AuthUser(int usuarioId, String username, String passwordHash,
                    boolean activo, Integer empleadoId) { }

    /** Owner de un refresh token activo (join con usuarios para datos frescos). */
    record RefreshOwner(int usuarioId, String username, Integer empleadoId) { }

    Optional<AuthUser> findByUsername(String username);

    List<String> rolesOf(int usuarioId);

    void saveRefreshToken(int usuarioId, String tokenHash, Instant expiresAt);

    Optional<RefreshOwner> findActiveRefreshOwner(String tokenHash, Instant now);

    void revokeByHash(String tokenHash);

    void updateUltimoLogin(int usuarioId);

    int abrirSesion(int usuarioId, String ip, String userAgent);

    void cerrarSesion(int sesionId);
}
