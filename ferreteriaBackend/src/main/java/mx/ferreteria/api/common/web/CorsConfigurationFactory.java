package mx.ferreteria.api.common.web;

import java.util.List;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Fabrica de {@link CorsConfiguration} y {@link CorsConfigurationSource} desde
 * {@link CorsProperties} (PLAN M7) — PASO 30 BACK-SEC-009 + PASO 34 CORS hardening fino.
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
 * <p>Notas de auditoria (PASO 30 docs + PASO 34 fail-closed):
 * <ul>
 *   <li>BACK-SEC-011: {@code "*"} + {@code allowCredentials=true} equivale a wildcard con
 *       credenciales. Spring 6 lo permite solo via {@code allowedOriginPatterns}; en prod
 *       debe sobreescribirse {@code CORS_ALLOWED_ORIGINS} con origenes explicitos. El default
 *       permisivo solo vale para dev (Vite 5173 / Angular CLI 4200).</li>
 *   <li>BACK-SEC-021 (PASO 34): {@code allowedHeaders=["*"]} con
 *       {@code allowCredentials=true} + {@code allowedOrigins=["*"]} lanza
 *       {@link IllegalStateException} via {@link #validate(CorsProperties)} (fail-closed).
 *       El spec Fetch permite {@code Access-Control-Allow-Headers: *} con credenciales, pero
 *       la politica del proyecto exige lista explicita cuando el origen es wildcard.</li>
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

    /**
     * Valida combinacion insegura BACK-SEC-021 (PASO 34 — CORS hardening fino / fail-closed).
     * <p>Lanza {@link IllegalStateException} si {@code allowCredentials=true} y
     * {@code allowedHeaders} contiene {@code "*"} mientras {@code allowedOrigins}
     * contiene {@code "*"} — en ese modo debe usarse lista explicita de headers.
     */
    public static void validate(CorsProperties props) {
        List<String> origins = props.allowedOrigins();
        List<String> headers = props.allowedHeaders();
        boolean wildcardOrigin = origins != null && origins.contains("*");
        boolean wildcardHeader = headers != null && headers.contains("*");
        if (props.allowCredentials() && wildcardOrigin && wildcardHeader) {
            throw new IllegalStateException(
                    "CORS misconfiguration (BACK-SEC-021): allowCredentials=true "
                            + "cannot be combined with allowedHeaders=\"*\" when allowedOrigins "
                            + "contains \"*\" — use explicit header list");
        }
    }

    public static CorsConfiguration configuration(CorsProperties props) {
        validate(props);
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
        // BACK-SEC-021 (PASO 34 — fail-closed): validate() arriba ya rechazo
        // allowedHeaders=["*"] + allowCredentials=true + allowedOrigins=["*"].
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
