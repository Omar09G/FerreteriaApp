package mx.ferreteria.api.seg.repo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.seg.service.SegAdminGateway;

/**
 * Adaptador JDBC del CRUD de seguridad. SQL nativo exacto al esquema seg.*
 * (02_tablas.sql). El borrado de usuarios es SOFT (eliminado_en); roles y
 * permisos no se borran, se desactivan (activo=false).
 */
@Repository
@RequiredArgsConstructor
public class SegAdminRepository implements SegAdminGateway {

    private static final String USUARIO_CAMPOS = """
            usuario_id, username, email, empleado_id, activo, ultimo_login, creado_en""";

    private final JdbcClient jdbc;

    @Override
    public List<UsuarioRow> findUsuarios(int limit, int offset) {
        return jdbc.sql("SELECT " + USUARIO_CAMPOS + " FROM seg.usuarios "
                        + "WHERE eliminado_en IS NULL ORDER BY usuario_id LIMIT :lim OFFSET :off")
                .param("lim", limit).param("off", offset)
                .query(this::mapUsuario)
                .list();
    }

    @Override
    public long countUsuarios() {
        Long n = jdbc.sql("SELECT count(*) FROM seg.usuarios WHERE eliminado_en IS NULL")
                .query(Long.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public Optional<UsuarioRow> findUsuarioById(int usuarioId) {
        return jdbc.sql("SELECT " + USUARIO_CAMPOS + " FROM seg.usuarios "
                        + "WHERE usuario_id = :id AND eliminado_en IS NULL")
                .param("id", usuarioId)
                .query(this::mapUsuario)
                .optional();
    }

    @Override
    public int createUsuario(String username, String email, String passwordHash, Integer empleadoId, boolean activo) {
        return jdbc.sql("""
                        INSERT INTO seg.usuarios (username, email, password_hash, empleado_id, activo)
                        VALUES (:u, :e, :h, :emp, :a) RETURNING usuario_id
                        """)
                .param("u", username).param("e", email).param("h", passwordHash)
                .param("emp", empleadoId).param("a", activo)
                .query(Integer.class)
                .single();
    }

    @Override
    public void updateUsuarioBasico(int usuarioId, String username, String email, Integer empleadoId, Boolean activo) {
        jdbc.sql("""
                        UPDATE seg.usuarios
                        SET username = COALESCE(:u, username),
                            email = COALESCE(:e, email),
                            empleado_id = COALESCE(:emp, empleado_id),
                            activo = COALESCE(:a, activo)
                        WHERE usuario_id = :id
                        """)
                .param("id", usuarioId).param("u", username).param("e", email)
                .param("emp", empleadoId).param("a", activo)
                .update();
    }

    @Override
    public void actualizarPassword(int usuarioId, String passwordHash) {
        jdbc.sql("UPDATE seg.usuarios SET password_hash = :h WHERE usuario_id = :id")
                .param("id", usuarioId).param("h", passwordHash)
                .update();
    }

    @Override
    public void borrarUsuario(int usuarioId) {
        jdbc.sql("UPDATE seg.usuarios SET eliminado_en = now(), activo = false "
                        + "WHERE usuario_id = :id AND eliminado_en IS NULL")
                .param("id", usuarioId)
                .update();
    }

    @Override
    public Set<String> rolClavesActivas() {
        return new LinkedHashSet<>(jdbc.sql(
                "SELECT clave FROM seg.roles WHERE activo ORDER BY rol_id")
                .query(String.class)
                .list());
    }

    @Override
    @Transactional
    public void reemplazarRoles(int usuarioId, Set<String> claves) {
        jdbc.sql("DELETE FROM seg.usuario_roles WHERE usuario_id = :id")
                .param("id", usuarioId)
                .update();
        for (String c : claves) {
            jdbc.sql("""
                            INSERT INTO seg.usuario_roles (usuario_id, rol_id)
                            SELECT :u, rol_id FROM seg.roles WHERE clave = :c AND activo
                            """)
                    .param("u", usuarioId).param("c", c)
                    .update();
        }
    }

    @Override
    public List<RolRow> findRoles(int limit, int offset) {
        return jdbc.sql("SELECT rol_id, clave, nombre, descripcion, activo FROM seg.roles "
                        + "ORDER BY rol_id LIMIT :lim OFFSET :off")
                .param("lim", limit).param("off", offset)
                .query(this::mapRol)
                .list();
    }

    @Override
    public long countRoles() {
        Long n = jdbc.sql("SELECT count(*) FROM seg.roles").query(Long.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public Optional<RolRow> findRolById(int rolId) {
        return jdbc.sql("SELECT rol_id, clave, nombre, descripcion, activo FROM seg.roles "
                        + "WHERE rol_id = :id")
                .param("id", rolId)
                .query(this::mapRol)
                .optional();
    }

    @Override
    public int createRol(String clave, String nombre, String descripcion, boolean activo) {
        return jdbc.sql("""
                        INSERT INTO seg.roles (clave, nombre, descripcion, activo)
                        VALUES (:c, :n, :d, :a) RETURNING rol_id
                        """)
                .param("c", clave).param("n", nombre).param("d", descripcion).param("a", activo)
                .query(Integer.class)
                .single();
    }

    @Override
    public void updateRol(int rolId, String nombre, String descripcion, Boolean activo) {
        jdbc.sql("""
                        UPDATE seg.roles
                        SET nombre = COALESCE(:n, nombre),
                            descripcion = COALESCE(:d, descripcion),
                            activo = COALESCE(:a, activo)
                        WHERE rol_id = :id
                        """)
                .param("id", rolId).param("n", nombre).param("d", descripcion).param("a", activo)
                .update();
    }

    @Override
    public void desactivarRol(int rolId) {
        jdbc.sql("UPDATE seg.roles SET activo = false WHERE rol_id = :id")
                .param("id", rolId)
                .update();
    }

    @Override
    public Set<String> permisoClaves() {
        return new LinkedHashSet<>(jdbc.sql("SELECT clave FROM seg.permisos ORDER BY permiso_id")
                .query(String.class)
                .list());
    }

    @Override
    public List<PermisoRow> findPermisos(int limit, int offset) {
        return jdbc.sql("SELECT permiso_id, clave, descripcion FROM seg.permisos "
                        + "ORDER BY permiso_id LIMIT :lim OFFSET :off")
                .param("lim", limit).param("off", offset)
                .query(this::mapPermiso)
                .list();
    }

    @Override
    public long countPermisos() {
        Long n = jdbc.sql("SELECT count(*) FROM seg.permisos").query(Long.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public Optional<PermisoRow> findPermisoById(int permisoId) {
        return jdbc.sql("SELECT permiso_id, clave, descripcion FROM seg.permisos "
                        + "WHERE permiso_id = :id")
                .param("id", permisoId)
                .query(this::mapPermiso)
                .optional();
    }

    @Override
    public Optional<PermisoRow> findPermisoByClave(String clave) {
        return jdbc.sql("SELECT permiso_id, clave, descripcion FROM seg.permisos "
                        + "WHERE clave = :c")
                .param("c", clave)
                .query(this::mapPermiso)
                .optional();
    }

    @Override
    public int createPermiso(String clave, String descripcion) {
        return jdbc.sql("""
                        INSERT INTO seg.permisos (clave, descripcion)
                        VALUES (:c, :d) RETURNING permiso_id
                        """)
                .param("c", clave).param("d", descripcion)
                .query(Integer.class)
                .single();
    }

    @Override
    public void updatePermiso(int permisoId, String clave, String descripcion) {
        jdbc.sql("""
                        UPDATE seg.permisos
                        SET clave = COALESCE(:c, clave),
                            descripcion = COALESCE(:d, descripcion)
                        WHERE permiso_id = :id
                        """)
                .param("id", permisoId).param("c", clave).param("d", descripcion)
                .update();
    }

    @Override
    public void deletePermiso(int permisoId) {
        jdbc.sql("DELETE FROM seg.permisos WHERE permiso_id = :id")
                .param("id", permisoId)
                .update();
    }

    @Override
    public List<String> permisosDe(int rolId) {
        return jdbc.sql("""
                        SELECT p.clave FROM seg.permisos p
                        JOIN seg.rol_permisos rp ON rp.permiso_id = p.permiso_id
                        WHERE rp.rol_id = :id ORDER BY p.permiso_id
                        """)
                .param("id", rolId)
                .query(String.class)
                .list();
    }

    @Override
    @Transactional
    public void reemplazarPermisos(int rolId, Set<String> claves) {
        jdbc.sql("DELETE FROM seg.rol_permisos WHERE rol_id = :id")
                .param("id", rolId)
                .update();
        for (String c : claves) {
            jdbc.sql("""
                            INSERT INTO seg.rol_permisos (rol_id, permiso_id)
                            SELECT :r, permiso_id FROM seg.permisos WHERE clave = :c
                            """)
                    .param("r", rolId).param("c", c)
                    .update();
        }
    }

    private UsuarioRow mapUsuario(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new UsuarioRow(rs.getInt("usuario_id"), rs.getString("username"),
                rs.getString("email"), (Integer) rs.getObject("empleado_id"),
                rs.getBoolean("activo"), toInstant(rs.getTimestamp("ultimo_login")),
                toInstant(rs.getTimestamp("creado_en")));
    }

    private RolRow mapRol(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new RolRow(rs.getInt("rol_id"), rs.getString("clave"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getBoolean("activo"));
    }

    private PermisoRow mapPermiso(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new PermisoRow(rs.getInt("permiso_id"), rs.getString("clave"),
                rs.getString("descripcion"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}