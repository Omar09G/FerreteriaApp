package mx.ferreteria.api.seg.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Regla de autorización del CRUD de seguridad (requisito: solo ADMINISTRADOR
 * actualiza ROLES/PERMISOS y crea usuarios). Se aplica a nivel de clase; un
 * método EXPLÍCITAMENTE con autorización menor rompería este test.
 */
class SegAdminSecurityTest {

    @Test
    @DisplayName("@PreAuthorize obligatorio y con rol ADMINISTRADOR en toda accion de escritura")
    void todaEscrituraDelApiRequiereRolAdministrador() {
        PreAuthorize clase = SegAdminController.class.getAnnotation(PreAuthorize.class);
        assertThat(clase).as("guard de clase obligatorio").isNotNull();
        assertThat(clase.value()).isEqualTo("hasRole('ADMINISTRADOR')");

        for (Method m : SegAdminController.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class)) {
                PreAuthorize metodo = m.getAnnotation(PreAuthorize.class);
                if (metodo != null) {
                    assertThat(metodo.value())
                            .as("%s.%s", m.getDeclaringClass().getSimpleName(), m.getName())
                            .startsWith("hasRole('ADMINISTRADOR')");
                }
            }
        }
    }
}