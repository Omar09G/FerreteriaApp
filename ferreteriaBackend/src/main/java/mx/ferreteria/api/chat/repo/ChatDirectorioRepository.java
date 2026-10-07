package mx.ferreteria.api.chat.repo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Directorio de usuarios para el chat (lectura de {@code seg.usuarios}).
 * Solo SQL de lectura, espejo de 02_tablas.sql. El chat no escribe en seg.
 */
@Repository
@RequiredArgsConstructor
public class ChatDirectorioRepository {

    private final JdbcClient jdbc;

    public record UsuarioChat(Integer usuarioId, String username) {
    }

    public boolean existeActivo(Integer usuarioId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT COUNT(*) = 1 FROM seg.usuarios u
                        WHERE u.usuario_id = :id AND u.activo AND u.eliminado_en IS NULL
                        """)
                .param("id", usuarioId)
                .query((rs, n) -> rs.getBoolean(1))
                .single());
    }

    /** Contactos: usuarios activos (para iniciar directas o armar grupos). */
    public List<UsuarioChat> contactosActivos() {
        return jdbc.sql("""
                        SELECT u.usuario_id, u.username FROM seg.usuarios u
                        WHERE u.activo AND u.eliminado_en IS NULL
                        ORDER BY u.username
                        """)
                .query((rs, n) -> new UsuarioChat(rs.getInt("usuario_id"), rs.getString("username")))
                .list();
    }

    public Map<Integer, String> nombresDe(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT u.usuario_id, u.username FROM seg.usuarios u
                        WHERE u.usuario_id IN (:ids)
                        """)
                .param("ids", ids)
                .query((rs, n) -> new UsuarioChat(rs.getInt("usuario_id"), rs.getString("username")))
                .list().stream()
                .collect(Collectors.toMap(UsuarioChat::usuarioId, UsuarioChat::username,
                        (a, b) -> a));
    }
}
