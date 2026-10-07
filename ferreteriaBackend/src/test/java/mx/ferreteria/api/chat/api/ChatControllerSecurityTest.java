package mx.ferreteria.api.chat.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ChatControllerSecurityTest {

    @Test
    @DisplayName("conversaciones requiere autenticación")
    void conversacionesRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("conversaciones");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("crear directa requiere autenticación")
    void crearDirectaRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("crearDirecta",
                mx.ferreteria.api.chat.dto.ChatDtos.CrearDirectaRequest.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("crear grupo requiere autenticación")
    void crearGrupoRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("crearGrupo",
                mx.ferreteria.api.chat.dto.ChatDtos.CrearGrupoRequest.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("historial requiere autenticación")
    void historialRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("historial", Long.class, Integer.class, Integer.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("enviar requiere autenticación")
    void enviarRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("enviar", Long.class,
                mx.ferreteria.api.chat.dto.ChatDtos.EnviarMensajeRequest.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }

    @Test
    @DisplayName("marcar leída requiere autenticación")
    void marcarLeidaRequiereAuth() throws Exception {
        var m = ChatController.class.getMethod("marcarLeida", Long.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("isAuthenticated()");
    }
}
