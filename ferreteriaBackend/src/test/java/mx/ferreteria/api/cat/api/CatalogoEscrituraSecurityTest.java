package mx.ferreteria.api.cat.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Matriz rol x verbo para catálogos custom: escritura exige rol (POST/PUT
 * ADMINISTRADOR/GERENTE, DELETE ADMINISTRADOR); lectura queda abierta a
 * cualquier autenticado para POS (ver README sprint S2).
 */
class CatalogoEscrituraSecurityTest {

    static Class<?>[] controllers() {
        return new Class<?>[] {
                ProductoController.class,
                ClienteController.class,
                ProveedorController.class,
                MarcaController.class,
                CategoriaController.class,
                UnidadMedidaController.class
        };
    }

    @ParameterizedTest(name = "{0} create/update exigen ADMINISTRADOR/GERENTE")
    @MethodSource("controllers")
    @DisplayName("create y update exigen ADMINISTRADOR/GERENTE")
    void escrituraRequiereRol(Class<?> controller) throws Exception {
        Method create = null;
        Method update = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals("create")) {
                create = m;
            }
            if (m.getName().equals("update")) {
                update = m;
            }
        }
        assertThat(create).isNotNull();
        assertThat(update).isNotNull();
        assertThat(create.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(create.getAnnotation(PreAuthorize.class).value())
                .contains("GERENTE").contains("ADMINISTRADOR");
        assertThat(update.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(update.getAnnotation(PreAuthorize.class).value())
                .contains("GERENTE").contains("ADMINISTRADOR");
    }

    @ParameterizedTest(name = "{0} deactivate exige ADMINISTRADOR")
    @MethodSource("controllers")
    @DisplayName("deactivate exige ADMINISTRADOR")
    void borradoRequiereAdmin(Class<?> controller) throws Exception {
        Method deactivate = null;
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals("deactivate")) {
                deactivate = m;
            }
        }
        assertThat(deactivate).isNotNull();
        assertThat(deactivate.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(deactivate.getAnnotation(PreAuthorize.class).value()).contains("ADMINISTRADOR");
    }
}
