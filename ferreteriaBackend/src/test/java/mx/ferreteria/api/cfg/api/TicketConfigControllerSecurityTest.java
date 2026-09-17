package mx.ferreteria.api.cfg.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class TicketConfigControllerSecurityTest {

    @Test
    @DisplayName("get exige rol de lectura (no solo authenticated)")
    void getRequiereRol() throws Exception {
        var m = TicketConfigController.class.getMethod("get", Integer.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("VENDEDOR").contains("GERENTE").contains("ADMINISTRADOR");
    }
}
