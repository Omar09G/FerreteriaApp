package mx.ferreteria.api.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ArchivoControllerSecurityTest {

    @Test
    @DisplayName("subirImagen exige rol operativo (ningún handler solo-authenticated)")
    void subirImagenRequiereRol() throws Exception {
        Method subir = ArchivoController.class.getDeclaredMethod("subirImagen",
                org.springframework.web.multipart.MultipartFile.class);
        PreAuthorize ann = subir.getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("ADMINISTRADOR").contains("GERENTE");
    }
}
