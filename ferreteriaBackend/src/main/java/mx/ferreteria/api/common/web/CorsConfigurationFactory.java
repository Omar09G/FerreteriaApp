package mx.ferreteria.api.common.web;

import java.util.List;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Fabrica de {@link CorsConfiguration} y {@link CorsConfigurationSource} desde
 * {@link CorsProperties} (PLAN M7).
 * <p>Reglas:
 * <ul>
 *   <li>Sin origenes configurados: {@code setAllowedOriginPatterns(List.of())}
 *       (politica vacia: rechaza cualquier cross-origin).</li>
 *   <li>Origenes incluyen {@code "*"} con credenciales: usa
 *       {@code setAllowedOriginPatterns(["*"])} (unico modo valido en Spring 6).</li>
 *   <li>Origenes incluyen {@code "*"} sin credenciales: usa {@code setAllowedOrigins(["*"])}.</li>
 *   <li>Origenes especificos: se copian tal cual a {@code setAllowedOrigins}.</li>
 * </ul>
 * El source se registra para el patron {@code /**}.
 */
public final class CorsConfigurationFactory {

    public static final String GLOBAL_PATTERN = "/**";

    private CorsConfigurationFactory() {
    }

    public static CorsConfiguration configuration(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = props.allowedOrigins();

        if (origins == null || origins.isEmpty()) {
            cfg.setAllowedOriginPatterns(List.of());
        } else if (origins.contains("*")) {
            if (props.allowCredentials()) {
                cfg.setAllowedOriginPatterns(List.of("*"));
            } else {
                cfg.setAllowedOrigins(List.of("*"));
            }
        } else {
            cfg.setAllowedOrigins(origins);
        }

        cfg.setAllowedMethods(props.allowedMethods());
        cfg.setAllowedHeaders(props.allowedHeaders());
        cfg.setExposedHeaders(props.exposedHeaders());
        cfg.setAllowCredentials(props.allowCredentials());
        cfg.setMaxAge(props.maxAgeSeconds());
        return cfg;
    }

    public static CorsConfigurationSource source(CorsProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(GLOBAL_PATTERN, configuration(props));
        return source;
    }
}
