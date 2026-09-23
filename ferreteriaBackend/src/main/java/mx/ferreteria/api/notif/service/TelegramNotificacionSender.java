package mx.ferreteria.api.notif.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;

/**
 * Telegram Bot API sendDocument (PDF adjunto). Solo se invoca si hay
 * bot token + chat id configurados.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class TelegramNotificacionSender {

    private final NotificacionProperties props;
    private RestClient restClient = RestClient.create();

    public void send(String chatId, String asunto, byte[] pdf, String clave) {
        String token = props.telegram().botToken();
        if (token == null || token.isBlank() || pdf == null) {
            return;
        }
        try {
            String nombre = clave != null && clave.contains("/")
                    ? clave.substring(clave.lastIndexOf('/') + 1)
                    : "documento.pdf";
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            form.add("caption", asunto != null ? asunto : "Documento");
            form.add("document", new org.springframework.core.io.ByteArrayResource(pdf) {
                @Override
                public String getFilename() {
                    return nombre;
                }
            });
            restClient.post()
                    .uri("https://api.telegram.org/bot{token}/sendDocument", token)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("telegram enviado chat_id={}", chatId);
        } catch (Exception e) {
            log.warn("telegram fallo chat_id={} err={}", chatId, e.getMessage());
        }
    }
}
