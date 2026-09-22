package mx.ferreteria.api.notif.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;

/**
 * Stub de WhatsApp: no llama a ningún proveedor (Meta/Twilio) hasta que
 * haya credenciales. Solo loguea la intención. Seguro por defecto.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class WhatsAppNotificacionSender {

    private final NotificacionProperties props;

    public WhatsAppNotificacionSender(NotificacionProperties props) {
        this.props = props;
    }

    public void send(String telefono, String asunto, byte[] pdf) {
        if (props.whatsapp() != null && props.whatsapp().enabled()) {
            log.info("whatsapp stub DISABLED — pendiente proveedor. to={} asunto={}",
                    telefono, asunto);
        } else {
            log.debug("whatsapp omitido (stub) to={}", telefono);
        }
    }
}
