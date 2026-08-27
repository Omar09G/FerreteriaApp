package mx.ferreteria.api.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Modos del header X-Request-Id (PLAN §4.5):
 *  - GENERATE (default): si falta se genera UUID v4 y se ecoa en la respuesta.
 *  - STRICT: si falta se rechaza con 400 FALTA_REQUEST_ID.
 * En ambos modos un header presente debe ser UUID válido o responde 400 REQUEST_ID_INVALIDO.
 */
@ConfigurationProperties(prefix = "app.request-id")
public record RequestIdProperties(@DefaultValue("GENERATE") Mode mode) {

    public enum Mode { GENERATE, STRICT }
}
