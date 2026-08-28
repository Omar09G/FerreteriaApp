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
 * Registro del interceptor de rate limit (PLAN M7) en rutas {@code /api/**}.
 * Vive en {@code common.config} (excluido del gate de cobertura JaCoCo).
 * Las dependencias se inyectan por constructor para evitar acoplamiento estatico.
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
                .addPathPatterns("/api/**");
    }
}
