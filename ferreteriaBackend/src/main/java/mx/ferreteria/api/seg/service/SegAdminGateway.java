package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Puerta de persistencia del CRUD de seguridad (usuarios/roles/permisos).
 * Implementación JDBC en {seg.repo}. Las invariantes y validaciones viven en
 * SegAdminService; aquí solo SQL exacto al esquema seg.*.
 */
public interface SegAdminGateway {

    record UsuarioRow(int usuarioId, String username, String email, Integer empleadoId,
                      boolean activo, Instant ultimoLogin, Instant creadoEn) { }

    record RolRow(int rolId, String clave, String nombre, String descripcion, boolean activo) { }

    record PermisoRow(int permisoId, String clave, String descripcion) { }

    List<UsuarioRow> findUsuarios(int limit, int offset);

    long countUsuarios();

    Optional<UsuarioRow> findUsuarioById(int usuarioId);

    int createUsuario(String username, String email, String passwordHash, Integer empleadoId, boolean activo);

    void updateUsuarioBasico(int usuarioId, String username, String email, Integer empleadoId, Boolean activo);

    void actualizarPassword(int usuarioId, String passwordHash);

    void borrarUsuario(int usuarioId);

    Set<String> rolClavesActivas();

    void reemplazarRoles(int usuarioId, Set<String> claves);

    List<RolRow> findRoles(int limit, int offset);

    long countRoles();

    Optional<RolRow> findRolById(int rolId);

    int createRol(String clave, String nombre, String descripcion, boolean activo);

    void updateRol(int rolId, String nombre, String descripcion, Boolean activo);

    void desactivarRol(int rolId);

    Set<String> permisoClaves();

    List<PermisoRow> findPermisos(int limit, int offset);

    long countPermisos();

    Optional<PermisoRow> findPermisoById(int permisoId);

    Optional<PermisoRow> findPermisoByClave(String clave);

    int createPermiso(String clave, String descripcion);

    void updatePermiso(int permisoId, String clave, String descripcion);

    void deletePermiso(int permisoId);

    List<String> permisosDe(int rolId);

    void reemplazarPermisos(int rolId, Set<String> claves);
}