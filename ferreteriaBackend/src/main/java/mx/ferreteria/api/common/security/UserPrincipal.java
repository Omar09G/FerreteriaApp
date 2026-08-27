package mx.ferreteria.api.common.security;

import java.security.Principal;
import java.util.List;

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
}
