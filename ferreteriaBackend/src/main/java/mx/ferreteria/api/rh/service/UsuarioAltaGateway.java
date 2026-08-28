package mx.ferreteria.api.rh.service;

import java.util.List;

/**
 * Puerto de ALTA de usuario del sistema (seg.usuarios) consumido por RH para
 * que POST /empleados pueda, en la misma función create, crear el usuario
 * ligado al empleado y asignarle roles. La implementación vive en seg
 * (dirección seg→rh, SIN ciclo de paquetes).
 */
public interface UsuarioAltaGateway {

    /**
     * Crea el usuario (BCrypt) ligado al empleado y le reemplaza los roles.
     * Clave de rol inexistente -> REFERENCIA_INVALIDA con rollback total.
     *
     * @return usuarioId creado
     */
    int crearUsuarioConRoles(String username, String email, String password,
                             int empleadoId, List<String> roles);
}