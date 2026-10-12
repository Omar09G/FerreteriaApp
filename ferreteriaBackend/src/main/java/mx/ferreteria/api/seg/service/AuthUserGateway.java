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

    Optional<AuthUser> findById(int usuarioId);

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findByGoogleSub(String googleSub);

    /** Destinos OTP: email de seg.usuarios + teléfono/whatsapp de rh.empleados. */
    Optional<Contactos> contactosDe(int usuarioId);

    record Contactos(String email, String telefono, String whatsapp) { }

    /** Vincula una cuenta local existente con Google (link por email verificado). */
    void linkGoogleAccount(int usuarioId, String googleSub);

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

    /**
     * True si el usuario tiene al menos un refresh vigente (no revocado y no
     * expirado): es la fuente de verdad de "sesión activa", porque el access
     * JWT es stateless y sigue válido hasta su expiración aunque el refresh
     * ya esté revocado.
     */
    boolean tieneSesionActiva(int usuarioId);

    /**
     * Cierra las filas abiertas de seg.sesiones (fin IS NULL) del usuario.
     * Se llama en cada emisión de sesión para que la bitácora no acumule
     * sesiones abiertas fantasma (p. ej. refresh vencido sin logout).
     */
    void cerrarSesionesActivas(int usuarioId);

    void updateUltimoLogin(int usuarioId);

    void incrementFailedAttempts(int usuarioId);

    void resetFailedAttempts(int usuarioId);

    int abrirSesion(int usuarioId, String ip, String userAgent,
            String ciudad, String region, String pais);

    void cerrarSesion(int sesionId);
}
