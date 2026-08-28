package mx.ferreteria.api.rh.api;

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
 * Regla: SOLO el rol ADMINISTRADOR crea/edita/baja empleados (base del alta de
 * usuarios). Guard a nivel de clase; un método con autorización menor rompería
 * este test.
 */
class EmpleadoControllerSecurityTest {

    @Test
    @DisplayName("@PreAuthorize obligatorio y con rol ADMINISTRADOR en toda accion de escritura")
    void todaEscrituraDelApiRequiereRolAdministrador() {
        PreAuthorize clase = EmpleadoController.class.getAnnotation(PreAuthorize.class);
        assertThat(clase).as("guard de clase obligatorio").isNotNull();
        assertThat(clase.value()).isEqualTo("hasRole('ADMINISTRADOR')");

        for (Method m : EmpleadoController.class.getDeclaredMethods()) {
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