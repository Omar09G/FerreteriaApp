package mx.ferreteria.api.common.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Bean de {@code @ConfigurationProperties} que el slice {@code @WebMvcTest}
 * no registra por defecto (solo escanea WebMvcConfigurer, filtros, etc.),
 * pero que {@code RateLimitConfig} requiere. Compartido por todos los tests
 * de slice web. (El {@code RequestIdProperties} lo define cada SliceConfig.)
 */
@TestConfiguration
public class WebMvcTestProps {

    @Bean
    RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties(true, true, 200_000, 30, java.util.Map.of());
    }
}
