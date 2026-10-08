package mx.ferreteria.api.cat.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CatalogoControllerSecurityTest {

    @Test
    @DisplayName("paneles requiere autenticación")
    void panelesRequiereAuth() {
        PreAuthorize ann = getAnnotation("paneles");
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("porClave requiere autenticación")
    void porClaveRequiereAuth() throws Exception {
        var m = CatalogoController.class.getMethod("porClave", String.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("opciones requiere autenticación")
    void opcionesRequiereAuth() throws Exception {
        var m = CatalogoController.class.getMethod("opciones", String.class, String.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    private static PreAuthorize getAnnotation(String method) {
        try {
            var m = CatalogoController.class.getMethod(method);
            return m.getAnnotation(PreAuthorize.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
