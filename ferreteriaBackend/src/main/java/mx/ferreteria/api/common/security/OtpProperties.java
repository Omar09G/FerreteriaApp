package mx.ferreteria.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Segundo factor OTP por email/WhatsApp (valores acordados: TTL 5 min,
 * máx 5 intentos, reenvío mínimo cada 60 s).
 */
@ConfigurationProperties(prefix = "app.auth.otp")
public record OtpProperties(
        @DefaultValue("5") int ttlMinutos,
        @DefaultValue("5") int maxIntentos,
        @DefaultValue("60") int reenvioSegundos,
        @DefaultValue("6") int longitud) {
}
