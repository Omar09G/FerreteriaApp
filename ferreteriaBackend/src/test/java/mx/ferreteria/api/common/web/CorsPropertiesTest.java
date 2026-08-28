package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CorsPropertiesTest {

    @Test
    @DisplayName("Bind completo desde propiedades planas: campos se mapean a lista/booleans/long")
    void bindCompleto() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("app.cors.allowed-origins[0]", "http://localhost:4200");
        raw.put("app.cors.allowed-origins[1]", "http://localhost:3000");
        raw.put("app.cors.allowed-methods[0]", "GET");
        raw.put("app.cors.allowed-methods[1]", "POST");
        raw.put("app.cors.allowed-headers[0]", "*");
        raw.put("app.cors.exposed-headers[0]", "X-Request-Id");
        raw.put("app.cors.max-age-seconds", "1800");
        raw.put("app.cors.allow-credentials", "true");

        CorsProperties props = bind(raw);
        assertThat(props.allowedOrigins()).containsExactly("http://localhost:4200", "http://localhost:3000");
        assertThat(props.allowedMethods()).containsExactly("GET", "POST");
        assertThat(props.allowedHeaders()).containsExactly("*");
        assertThat(props.exposedHeaders()).containsExactly("X-Request-Id");
        assertThat(props.maxAgeSeconds()).isEqualTo(1800L);
        assertThat(props.allowCredentials()).isTrue();
    }

    @Test
    @DisplayName("Defaults: una sola clave del namespace basta para activar el binding")
    void defaults() {
        // Sin ninguna clave en el namespace, Binder.bind() retorna Optional vacio.
        // Una clave del namespace (aunque no altere un campo) garantiza que se cree el bean
        // y se apliquen los @DefaultValue del record.
        Map<String, Object> raw = Map.of("app.cors.max-age-seconds", "3600");
        CorsProperties props = bind(raw);

        assertThat(props.allowedOrigins()).isEmpty();
        assertThat(props.allowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
        assertThat(props.allowedHeaders()).containsExactly("*");
        assertThat(props.exposedHeaders()).isEmpty();
        assertThat(props.maxAgeSeconds()).isEqualTo(3600L);
        assertThat(props.allowCredentials()).isFalse();
    }

    private CorsProperties bind(Map<String, Object> raw) {
        Binder binder = new Binder(new MapConfigurationPropertySource(raw));
        return binder.bind("app.cors", CorsProperties.class).get();
    }
}
