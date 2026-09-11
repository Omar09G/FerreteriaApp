package mx.ferreteria.api.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimitInterceptor;
import mx.ferreteria.api.common.web.RateLimitProperties;

/**
 * Registro del interceptor de rate limit (PLAN M7) en TODAS las rutas.
 * Cubre /api/** y también las no definidas (/, /favicon.ico, actuator,
 * swagger): esas usan la bolsa común "default:sin-handler:ip" para frenar
 * scanners. Ver RateLimitInterceptor (los controllers reales mantienen su
 * propio bucket y no se ven afectados).
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;
    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimitProperties, messages, objectMapper))
                .addPathPatterns("/**");
    }
}
