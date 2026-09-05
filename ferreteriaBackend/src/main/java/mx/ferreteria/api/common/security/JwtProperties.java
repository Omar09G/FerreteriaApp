package mx.ferreteria.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config JWT (PLAN §9). Secret >=32 bytes; se valida al construir JwtService.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
                String secret,
                /**
                 * BACK-SEC-033: previousSecret opcional. Si esta presente y es >= 32 bytes,
                 * JwtService valida tokens firmados con esta clave ademas del actual.
                 * Usar durante una ventana corta tras rotar JWT_SECRET para no forzar
                 * re-login masivo. Dejar vacio cuando no se este rotando.
                 */
                String previousSecret,
                @DefaultValue("15") int accessMinutes,
                @DefaultValue("8") int refreshHours) {
}
