package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto de identidad desacoplado del almacenamiento (testeable sin BD). */
public interface AuthUserGateway {

    record AuthUser(int usuarioId, String username, String passwordHash,
                    boolean activo, boolean debeCambiarPassword, Integer empleadoId,
                    int failedLoginAttempts, Instant lockedUntil) {
        public AuthUser(int usuarioId, String username, String passwordHash,
                        boolean activo, boolean debeCambiarPassword, Integer empleadoId) {
            this(usuarioId, username, passwordHash, activo, debeCambiarPassword, empleadoId, 0, null);
        }
    }

    /** Owner de un refresh token activo (join con usuarios para datos frescos). */
    record RefreshOwner(int usuarioId, String username, Integer empleadoId) { }

    /** Estado DB de un refresh token (sin joins) para distinguir revocado/expirado/activo. */
    record RefreshRow(int usuarioId, Instant expiresAt, Instant revokedAt) { }

    Optional<AuthUser> findByUsername(String username);

    List<String> rolesOf(int usuarioId);

    /** Roles agrupados por usuarioId (orden estable por rol_id). Para evitar N+1 al listar. */
    Map<Integer, List<String>> rolesOfBatch(Collection<Integer> usuarioIds);

    void saveRefreshToken(int usuarioId, String tokenHash, Instant expiresAt);

    Optional<RefreshOwner> findActiveRefreshOwner(String tokenHash, Instant now);

    Optional<RefreshRow> findRefreshRow(String tokenHash);

    /**
     * Revoca atómicamente el refresh token si no estaba revocado.
     * @return true si este llamado ganó la carrera (revocó), false si ya estaba revocado/expirado.
     */
    boolean revokeByHash(String tokenHash);

    /** Revoca TODAS las sesiones previas del usuario (login nuevo = single-active). */
    void revokeAllRefreshTokens(int usuarioId);

    void updateUltimoLogin(int usuarioId);

    void incrementFailedAttempts(int usuarioId);

    void resetFailedAttempts(int usuarioId);

    int abrirSesion(int usuarioId, String ip, String userAgent);

    void cerrarSesion(int sesionId);
}
