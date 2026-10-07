package mx.ferreteria.api.notif.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BandejaControllerSecurityTest {

    @Test
    @DisplayName("stream requiere autenticación")
    void streamRequiereAuth() throws Exception {
        var m = BandejaController.class.getMethod("stream");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("listar requiere autenticación")
    void listarRequiereAuth() throws Exception {
        var m = BandejaController.class.getMethod("listar", Integer.class, Integer.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("no-leidas requiere autenticación")
    void noLeidasRequiereAuth() throws Exception {
        var m = BandejaController.class.getMethod("noLeidas");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("marcar leída requiere autenticación")
    void marcarLeidaRequiereAuth() throws Exception {
        var m = BandejaController.class.getMethod("marcarLeida", Long.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("marcar todas requiere autenticación")
    void marcarTodasRequiereAuth() throws Exception {
        var m = BandejaController.class.getMethod("marcarTodasLeidas");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }
}
