package mx.ferreteria.api.cat.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * BACK-SEC-001: Configuracion y Folio solo ADMINISTRADOR (fraude financiero).
 */
class ConfiguracionFolioSecurityTest {

    @Test
    @DisplayName("ConfiguracionController requiere ADMINISTRADOR")
    void configuracionRequiereAdmin() {
        PreAuthorize ann = ConfiguracionController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann).as("ConfiguracionController debe tener @PreAuthorize").isNotNull();
        assertThat(ann.value()).isEqualTo("hasRole('ADMINISTRADOR')");
    }

    @Test
    @DisplayName("FolioController requiere ADMINISTRADOR")
    void folioRequiereAdmin() {
        PreAuthorize ann = FolioController.class.getAnnotation(PreAuthorize.class);
        assertThat(ann).as("FolioController debe tener @PreAuthorize").isNotNull();
        assertThat(ann.value()).isEqualTo("hasRole('ADMINISTRADOR')");
    }
}
