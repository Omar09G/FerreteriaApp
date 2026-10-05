package mx.ferreteria.api.seg.repo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.ferreteria.api.seg.service.OtpGateway;

/**
 * Adaptador JDBC de desafíos OTP (SQL exacto a seg.otp_desafios).
 */
@Repository
@RequiredArgsConstructor
public class OtpRepository implements OtpGateway {

    private final JdbcClient jdbc;

    @Override
    public void insertar(int usuarioId, String challengeId, Instant expiraEn) {
        jdbc.sql("""
                INSERT INTO seg.otp_desafios (challenge_id, usuario_id, expira_en)
                VALUES (:c, :u, :e)
                """)
                .param("c", challengeId).param("u", usuarioId)
                .param("e", Timestamp.from(expiraEn))
                .update();
    }

    @Override
    public void revocarActivos(int usuarioId) {
        jdbc.sql("""
                UPDATE seg.otp_desafios SET revocado_en = now()
                WHERE usuario_id = :u AND consumido_en IS NULL AND revocado_en IS NULL
                """)
                .param("u", usuarioId)
                .update();
    }

    @Override
    public Optional<Desafio> findByChallengeId(String challengeId) {
        return jdbc.sql("""
                SELECT otp_id, challenge_id, usuario_id, proposito, canal, codigo_hash,
                       intentos, expira_en, enviado_en, consumido_en, revocado_en
                FROM seg.otp_desafios WHERE challenge_id = :c
                """)
                .param("c", challengeId)
                .query((rs, n) -> new Desafio(rs.getLong("otp_id"),
                        rs.getString("challenge_id"), rs.getInt("usuario_id"),
                        rs.getString("canal"), rs.getString("codigo_hash"),
                        rs.getInt("intentos"),
                        rs.getTimestamp("expira_en").toInstant(),
                        ts(rs, "enviado_en"), ts(rs, "consumido_en"), ts(rs, "revocado_en")))
                .optional();
    }

    @Override
    public void fijarCodigo(String challengeId, String canal, String codigoHash, Instant expiraEn) {
        jdbc.sql("""
                UPDATE seg.otp_desafios
                SET canal = :canal, codigo_hash = :h, enviado_en = now(), expira_en = :e
                WHERE challenge_id = :c
                """)
                .param("canal", canal).param("h", codigoHash)
                .param("e", Timestamp.from(expiraEn)).param("c", challengeId)
                .update();
    }

    @Override
    public void incrementarIntentos(String challengeId) {
        jdbc.sql("UPDATE seg.otp_desafios SET intentos = intentos + 1 WHERE challenge_id = :c")
                .param("c", challengeId)
                .update();
    }

    @Override
    public void consumir(String challengeId) {
        jdbc.sql("UPDATE seg.otp_desafios SET consumido_en = now() WHERE challenge_id = :c")
                .param("c", challengeId)
                .update();
    }

    @Override
    public void revocar(String challengeId) {
        jdbc.sql("UPDATE seg.otp_desafios SET revocado_en = now() WHERE challenge_id = :c")
                .param("c", challengeId)
                .update();
    }

    private static Instant ts(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
