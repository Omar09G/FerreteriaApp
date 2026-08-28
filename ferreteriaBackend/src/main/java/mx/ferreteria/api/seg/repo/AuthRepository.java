package mx.ferreteria.api.seg.repo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.ferreteria.api.seg.service.AuthUserGateway;

/**
 * Adaptador JDBC de identidad (lecturas puntuales; entidades JPA llegan con
 * M2).
 * SQL nativo exacto al esquema seg.* real.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthRepository implements AuthUserGateway {

        private final JdbcClient jdbc;

        @Override
        public Optional<AuthUser> findByUsername(String username) {
                return jdbc.sql("""
                                SELECT usuario_id, username, password_hash, activo, empleado_id
                                FROM seg.usuarios WHERE username = :u AND eliminado_en IS NULL
                                """)
                                .param("u", username)
                                .query((rs, n) -> new AuthUser(rs.getInt("usuario_id"), rs.getString("username"),
                                                rs.getString("password_hash"), rs.getBoolean("activo"),
                                                (Integer) rs.getObject("empleado_id")))
                                .optional();
        }

        @Override
        public List<String> rolesOf(int usuarioId) {
                return jdbc.sql("""
                                SELECT r.clave FROM seg.roles r
                                JOIN seg.usuario_roles ur ON ur.rol_id = r.rol_id
                                WHERE ur.usuario_id = :id AND r.activo
                                ORDER BY r.rol_id
                                """)
                                .param("id", usuarioId)
                                .query(String.class)
                                .list();
        }

        @Override
        public void saveRefreshToken(int usuarioId, String tokenHash, Instant expiresAt) {
                jdbc.sql("""
                                INSERT INTO seg.refresh_tokens (usuario_id, token_hash, expires_at)
                                VALUES (:u, :h, :e)
                                """)
                                .param("u", usuarioId).param("h", tokenHash)
                                .param("e", Timestamp.from(expiresAt))
                                .update();
        }

        @Override
        public Optional<RefreshOwner> findActiveRefreshOwner(String tokenHash, Instant now) {
                return jdbc.sql("""
                                SELECT u.usuario_id, u.username, u.empleado_id
                                FROM seg.refresh_tokens rt
                                JOIN seg.usuarios u ON u.usuario_id = rt.usuario_id
                                WHERE rt.token_hash = :h AND rt.revoked_at IS NULL
                                  AND rt.expires_at > :now AND u.activo AND u.eliminado_en IS NULL
                                """)
                                .param("h", tokenHash).param("now", Timestamp.from(now))
                                .query((rs, n) -> new RefreshOwner(rs.getInt("usuario_id"),
                                                rs.getString("username"), (Integer) rs.getObject("empleado_id")))
                                .optional();
        }

        @Override
        public Optional<RefreshRow> findRefreshRow(String tokenHash) {
                return jdbc.sql("""
                                SELECT usuario_id, expires_at, revoked_at
                                FROM seg.refresh_tokens WHERE token_hash = :h
                                """)
                                .param("h", tokenHash)
                                .query((rs, n) -> {
                                        Timestamp rev = rs.getTimestamp("revoked_at");
                                        return new RefreshRow(rs.getInt("usuario_id"),
                                                        rs.getTimestamp("expires_at").toInstant(),
                                                        rev == null ? null : rev.toInstant());
                                })
                                .optional();
        }

        @Override
        public void revokeByHash(String tokenHash) {
                jdbc.sql("UPDATE seg.refresh_tokens SET revoked_at = now() "
                                + "WHERE token_hash = :h AND revoked_at IS NULL")
                                .param("h", tokenHash)
                                .update();
        }

        @Override
        public void revokeAllRefreshTokens(int usuarioId) {
                jdbc.sql("UPDATE seg.refresh_tokens SET revoked_at = now() "
                                + "WHERE usuario_id = :id AND revoked_at IS NULL")
                                .param("id", usuarioId)
                                .update();
        }

        @Override
        public void updateUltimoLogin(int usuarioId) {
                jdbc.sql("UPDATE seg.usuarios SET ultimo_login = now() WHERE usuario_id = :id")
                                .param("id", usuarioId)
                                .update();
        }

        @Override
        public int abrirSesion(int usuarioId, String ip, String userAgent) {
                return jdbc.sql("""
                                INSERT INTO seg.sesiones (usuario_id, ip_address, user_agent)
                                VALUES (:u, CAST(COALESCE(:ip,'0.0.0.0') AS inet), COALESCE(:ua,''))
                                RETURNING sesion_id
                                """)
                                .param("u", usuarioId).param("ip", ip).param("ua", userAgent)
                                .query(Integer.class)
                                .single();
        }

        @Override
        public void cerrarSesion(int sesionId) {
                jdbc.sql("UPDATE seg.sesiones SET fin = now(), cerrada_por_logout = true "
                                + "WHERE sesion_id = :id AND fin IS NULL")
                                .param("id", sesionId)
                                .update();
        }
}
