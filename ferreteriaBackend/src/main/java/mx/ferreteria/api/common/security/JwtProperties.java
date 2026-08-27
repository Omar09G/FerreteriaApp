package mx.ferreteria.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config JWT (PLAN §9). Secret >=32 bytes; se valida al construir JwtService.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
                String secret,
                @DefaultValue("15") int accessMinutes,
                @DefaultValue("8") int refreshHours) {
}
