package mx.ferreteria.api.seg.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de desafíos OTP de un solo uso (seg.otp_desafios).
 * El código viaja hasheado (SHA-256); el id del desafío es un token opaco.
 */
public interface OtpGateway {

    record Desafio(long otpId, String challengeId, int usuarioId, String canal,
                   String codigoHash, int intentos, Instant expiraEn, Instant enviadoEn,
                   Instant consumidoEn, Instant revocadoEn) {
        boolean vigente(Instant ahora) {
            return consumidoEn == null && revocadoEn == null && expiraEn.isAfter(ahora);
        }
    }

    /**
     * Crea un desafío pendiente: revoca los activos previos del usuario y
     * devuelve el id opaco. El código se fija después en
     * {@link #fijarCodigo}.
     */
    default String crearDesafio(int usuarioId, Duration ttl) {
        revocarActivos(usuarioId);
        String challengeId = UUID.randomUUID().toString();
        insertar(usuarioId, challengeId, Instant.now().plus(ttl));
        return challengeId;
    }

    void insertar(int usuarioId, String challengeId, Instant expiraEn);

    void revocarActivos(int usuarioId);

    Optional<Desafio> findByChallengeId(String challengeId);

    /** Fija el hash del código + canal y marca el envío (aplica reenvío). */
    void fijarCodigo(String challengeId, String canal, String codigoHash, Instant expiraEn);

    void incrementarIntentos(String challengeId);

    void consumir(String challengeId);

    void revocar(String challengeId);
}
