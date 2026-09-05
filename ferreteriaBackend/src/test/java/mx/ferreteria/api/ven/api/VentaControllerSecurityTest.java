package mx.ferreteria.api.ven.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class VentaControllerSecurityTest {

    @Test
    @DisplayName("checkout requiere VENDEDOR/GERENTE/ADMINISTRADOR")
    void checkoutRequiereRol() throws Exception {
        var m = VentaController.class.getMethod("checkout", mx.ferreteria.api.ven.dto.VenDtos.VentaRequest.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("VENDEDOR").contains("GERENTE").contains("ADMINISTRADOR");
    }

    @Test
    @DisplayName("cancel requiere GERENTE/ADMINISTRADOR")
    void cancelRequiereRol() throws Exception {
        var m = VentaController.class.getMethod("cancel", Long.class, mx.ferreteria.api.ven.dto.VenDtos.VentaCancelRequest.class);
        PreAuthorize ann = m.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("GERENTE").contains("ADMINISTRADOR");
    }
}
