package mx.ferreteria.api.rh.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class NominaControllerSecurityTest {

    @Test
    @DisplayName("controller exige ADMINISTRADOR/GERENTE a nivel clase (ningún handler solo-authenticated)")
    void claseRequiereRol() {
        PreAuthorize ann = NominaController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("GERENTE").contains("ADMINISTRADOR");
    }
}
