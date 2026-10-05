package mx.ferreteria.api.notif.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RentasInformeControllerSecurityTest {

    @Test
    @DisplayName("enviar requiere GERENTE/ADMINISTRADOR")
    void enviarRequiereRol() throws Exception {
        var m = RentasInformeController.class.getMethod("enviar");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("GERENTE").contains("ADMINISTRADOR");
    }

    @Test
    @DisplayName("estado requiere GERENTE/ADMINISTRADOR")
    void estadoRequiereRol() throws Exception {
        var m = RentasInformeController.class.getMethod("estado");
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("GERENTE").contains("ADMINISTRADOR");
    }
}
