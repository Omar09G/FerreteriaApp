package mx.ferreteria.api.notif.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class TurnoAbiertoInformeControllerSecurityTest {

    @Test
    @DisplayName("controller exige GERENTE/ADMINISTRADOR a nivel clase (cubre enviar y estado)")
    void controllerRequiereRol() {
        PreAuthorize ann = TurnoAbiertoInformeController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("GERENTE").contains("ADMINISTRADOR");
    }
}
