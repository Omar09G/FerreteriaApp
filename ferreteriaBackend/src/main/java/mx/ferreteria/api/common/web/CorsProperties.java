package mx.ferreteria.api.common.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion de CORS (PLAN M7). Parametrizable por .env:
 * origenes, metodos, cabeceras, expuestas, edad maxima y credenciales.
 *
 * <p>Por defecto, sin origenes configurados, {@link CorsConfigurationFactory}
 * genera una politica restrictiva que rechaza cualquier cross-origin (modo seguro).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue({}) List<String> allowedOrigins,
        @DefaultValue({ "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD" }) List<String> allowedMethods,
        @DefaultValue({ "*" }) List<String> allowedHeaders,
        @DefaultValue({}) List<String> exposedHeaders,
        @DefaultValue("3600") long maxAgeSeconds,
        @DefaultValue("false") boolean allowCredentials) {
}
