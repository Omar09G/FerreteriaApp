package mx.ferreteria.api.common.security;

import java.security.Principal;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Principal autenticado que viaja en el SecurityContext y dentro del access token. */
public record UserPrincipal(
        int usuarioId,
        String username,
        Integer empleadoId,
        List<String> roles) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public static final UserPrincipal SYSTEM = new UserPrincipal(0, "system", null, List.of());

    /**
     * Devuelve el principal del SecurityContext, o {@link #SYSTEM} si no hay sesión
     * (p.ej. en tareas programadas, seeds o tests). NO usar en endpoints protegidos:
     * ahí la auth-falla antes y nunca llegamos aquí sin principal.
     */
    public static UserPrincipal actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return SYSTEM;
        Object principal = auth.getPrincipal();
        return (principal instanceof UserPrincipal up) ? up : SYSTEM;
    }
}
