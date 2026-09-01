package mx.ferreteria.api.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config de las cookies HttpOnly de autenticación.
 *
 * <p>Tanto el access token como el refresh token viajan en cookies HttpOnly.
 * El frontend NUNCA los lee desde JS: el browser los adjunta automáticamente
 * gracias a {@code withCredentials: true} en axios.</p>
 *
 * <ul>
 * <li>{@code secure}: solo true en HTTPS (prod). En dev (http://localhost) debe ser false
 * para que el browser la acepte.</li>
 * <li>{@code sameSite}: {@code Strict} para Same-Origin estricto (anti-CSRF);
 * {@code Lax} si front y back viven en hostnames distintos (proxy dev).</li>
 * <li>{@code path}: scope de la cookie. El refresh vive en
 * {@code /api/v1/auth}; el access debe estar disponible para TODA la API, así
 * que va en {@code /}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
public record AuthCookieProperties(
        @DefaultValue("false") boolean secure,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("/") String path,
        @DefaultValue("rt") String refreshName,
        @DefaultValue("at") String accessName) {

    public Duration maxAge(Duration refreshTtl) {
        return refreshTtl;
    }

    /** SameSite normalizado, tal como lo espera {@code ResponseCookieBuilder.sameSite(String)}. */
    public String sameSiteMode() {
        return sameSite == null || sameSite.isBlank() ? "Lax" : sameSite.trim();
    }
}
