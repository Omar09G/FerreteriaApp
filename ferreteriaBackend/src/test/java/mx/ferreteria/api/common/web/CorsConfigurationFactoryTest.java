package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigurationFactoryTest {

    @Test
    @DisplayName("Sin origenes: politica vacia (rechaza cualquier cross-origin)")
    void sinOrigenesPoliticaVacia() {
        CorsProperties props = new CorsProperties(List.of(), List.of("GET"), List.of("*"), List.of(), 60L, false);
        CorsConfiguration cfg = CorsConfigurationFactory.configuration(props);

        assertThat(cfg).isNotNull();
        // Cuando no hay origenes configurados el factory solo escribe allowedOriginPatterns.
        // allowedOrigins queda en null (contrato de Spring), por eso isNullOrEmpty.
        assertThat(cfg.getAllowedOrigins()).isNullOrEmpty();
        assertThat(cfg.getAllowedOriginPatterns()).isEmpty();
        assertThat(cfg.getAllowedMethods()).containsExactly("GET");
        assertThat(cfg.getMaxAge()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Origenes especificos: se copian tal cual a allowedOrigins")
    void origenesEspecificos() {
        CorsProperties props = new CorsProperties(
                List.of("http://localhost:4200", "http://localhost:3000"),
                List.of("GET", "POST"),
                List.of("*"),
                List.of("X-Request-Id"),
                120L,
                false);
        CorsConfiguration cfg = CorsConfigurationFactory.configuration(props);

        assertThat(cfg.getAllowedOrigins()).containsExactlyInAnyOrder("http://localhost:4200", "http://localhost:3000");
        assertThat(cfg.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(cfg.getExposedHeaders()).containsExactly("X-Request-Id");
        assertThat(cfg.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST");
    }

    @Test
    @DisplayName("Wildcard '*' SIN credenciales: setAllowedOrigins(['*'])")
    void wildcardSinCredenciales() {
        CorsProperties props = new CorsProperties(
                List.of("*"),
                List.of("GET"),
                List.of("*"),
                List.of(),
                0L,
                false);
        CorsConfiguration cfg = CorsConfigurationFactory.configuration(props);

        assertThat(cfg.getAllowedOrigins()).containsExactly("*");
        assertThat(cfg.getAllowedOriginPatterns()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Wildcard '*' CON credenciales: setAllowedOriginPatterns(['*']) (Spring 6)")
    void wildcardConCredenciales() {
        CorsProperties props = new CorsProperties(
                List.of("*"),
                List.of("GET"),
                List.of("*"),
                List.of(),
                0L,
                true);
        CorsConfiguration cfg = CorsConfigurationFactory.configuration(props);

        assertThat(cfg.getAllowedOrigins()).isNullOrEmpty();
        assertThat(cfg.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(cfg.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("MaxAge, allowedHeaders, exposedHeaders se reflejan tal cual")
    void cabecerasYMaxAge() {
        CorsProperties props = new CorsProperties(
                List.of("http://app.local"),
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Type"),
                List.of("X-Request-Id", "X-RateLimit-Limit"),
                3600L,
                false);
        CorsConfiguration cfg = CorsConfigurationFactory.configuration(props);

        assertThat(cfg.getMaxAge()).isEqualTo(3600L);
        assertThat(cfg.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type");
        assertThat(cfg.getExposedHeaders()).containsExactlyInAnyOrder("X-Request-Id", "X-RateLimit-Limit");
        assertThat(cfg.getAllowedMethods()).containsExactlyInAnyOrder(
                "GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("source(props) envuelve la misma configuracion en UrlBasedCorsConfigurationSource")
    void sourceEnvolverConfiguration() {
        CorsProperties props = new CorsProperties(
                List.of("http://app.local"),
                List.of("GET"),
                List.of("*"),
                List.of(),
                60L,
                false);
        CorsConfiguration directo = CorsConfigurationFactory.configuration(props);

        UrlBasedCorsConfigurationSource src =
                (UrlBasedCorsConfigurationSource) CorsConfigurationFactory.source(props);
        CorsConfiguration envuelto = src.getCorsConfigurations().get(CorsConfigurationFactory.GLOBAL_PATTERN);

        assertThat(envuelto).isNotNull();
        assertThat(envuelto.getAllowedOrigins()).isEqualTo(directo.getAllowedOrigins());
        assertThat(envuelto.getAllowedMethods()).isEqualTo(directo.getAllowedMethods());
        assertThat(envuelto.getAllowedHeaders()).isEqualTo(directo.getAllowedHeaders());
        assertThat(envuelto.getExposedHeaders()).isEqualTo(directo.getExposedHeaders());
        assertThat(envuelto.getMaxAge()).isEqualTo(directo.getMaxAge());
        assertThat(envuelto.getAllowCredentials()).isEqualTo(directo.getAllowCredentials());
    }
}
