package mx.ferreteria.api.common.web;

import java.util.List;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Fabrica de {@link CorsConfiguration} y {@link CorsConfigurationSource} desde
 * {@link CorsProperties} (PLAN M7) — PASO 30 BACK-SEC-009 + CORS hardening fino.
 * <p>BACK-SEC-009 (sort whitelist) vive en {@link PageQuery}; esta clase cubre el
 * hardening CORS restante (BACK-SEC-011 / BACK-SEC-021 / BACK-MAN-008).
 * <p>Reglas de origen:
 * <ul>
 *   <li>Sin origenes configurados: {@code setAllowedOriginPatterns(List.of())}
 *       (politica vacia: rechaza cualquier cross-origin).</li>
 *   <li>Origenes incluyen {@code "*"} con credenciales: usa
 *       {@code setAllowedOriginPatterns(["*"])} (unico modo valido en Spring 6).</li>
 *   <li>Origenes incluyen {@code "*"} sin credenciales: usa {@code setAllowedOrigins(["*"])}.</li>
 *   <li>Origenes especificos: se copian tal cual a {@code setAllowedOrigins}.</li>
 * </ul>
 * <p>Notas de auditoria (PASO 30 — solo documentacion, sin cambio de comportamiento):
 * <ul>
 *   <li>BACK-SEC-011: {@code "*"} + {@code allowCredentials=true} equivale a wildcard con
 *       credenciales. Spring 6 lo permite solo via {@code allowedOriginPatterns}; en prod
 *       debe sobreescribirse {@code CORS_ALLOWED_ORIGINS} con origenes explicitos. El default
 *       permisivo solo vale para dev (Vite 5173 / Angular CLI 4200).</li>
 *   <li>BACK-SEC-021: {@code allowedHeaders=["*"]} con {@code allowCredentials=true} se evaluo
 *       con {@code IllegalStateException} y se revertio. El spec Fetch permite
 *       {@code Access-Control-Allow-Headers: *} aun con credenciales; Spring refleja el valor
 *       tal cual. Bloquearlo romperia preflights con headers custom en dev. Futuro fail-closed:
 *       validar al arranque y exigir lista explicita si se endurece politica.</li>
 *   <li>BACK-MAN-008: {@code "*"} / {@code "**"} como magic values — {@code "*"} es literal
 *       del spec CORS; {@link #GLOBAL_PATTERN} centraliza {@code "/**"}.</li>
 * </ul>
 * El source se registra para el patron {@code /**} y se expone como {@code @Bean} en
 * {@link mx.ferreteria.api.common.config.SecurityConfig#corsConfigurationSource()}.
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
        // BACK-SEC-021 (PASO 30): allowedHeaders=["*"] con allowCredentials=true no lanza
        // excepcion por decision consciente (revertido). Ver javadoc de clase.
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
