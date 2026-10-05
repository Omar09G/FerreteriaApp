package mx.ferreteria.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 Google con redirect iniciado por el backend: el secret nunca llega
 * al frontend. Sin credenciales configuradas el bean existe pero inactivo
 * (el servicio falla con OAUTH_FALLIDO al usarse).
 */
@ConfigurationProperties(prefix = "app.auth.google")
public record GoogleAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String frontendCallbackUrl) {

    public boolean configurado() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
