package mx.ferreteria.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretosProdGuardTest {

    @ParameterizedTest(name = "placeholder: {0}")
    @ValueSource(strings = {"", "cambia_app_seguro", "cambia_minio_seguro",
            "cambia_rabbit_seguro", "cambia_loquesea", "minioadmin", "MINIOADMIN",
            "ferreteria", "test", "guest", "CAMBIAR_dev_only", "  minioadmin  "})
    @DisplayName("detecta secretos placeholder")
    void detectaPlaceholders(String valor) {
        assertThat(SecretosProdGuard.esPlaceholder(valor)).isTrue();
    }

    @ParameterizedTest(name = "secreto real: {0}")
    @ValueSource(strings = {"xK9#mQ2$vL8pN4wZ7!", "openssl-rand-base64-48-valor-largo"})
    @DisplayName("acepta secretos reales")
    void aceptaReales(String valor) {
        assertThat(SecretosProdGuard.esPlaceholder(valor)).isFalse();
    }

    @Test
    @DisplayName("perfil docker es contexto prod")
    void dockerEsProd() {
        assertThat(SecretosProdGuard.esContextoProd(List.of("docker"), false, "")).isTrue();
    }

    @Test
    @DisplayName("cookie secure es contexto prod")
    void secureEsProd() {
        assertThat(SecretosProdGuard.esContextoProd(List.of("default"), true, "")).isTrue();
    }

    @Test
    @DisplayName("FERRETERIA_ENV=prod es contexto prod")
    void envProdEsProd() {
        assertThat(SecretosProdGuard.esContextoProd(List.of("default"), false, "prod")).isTrue();
    }

    @Test
    @DisplayName("dev local sin señales no es contexto prod")
    void devNoEsProd() {
        assertThat(SecretosProdGuard.esContextoProd(List.of("default"), false, "")).isFalse();
    }
}
