package mx.ferreteria.api.notif.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;

/**
 * Envío multi-canal de un mensaje ya publicado. WhatsApp es stub no-op
 * hasta proveedor real; Telegram solo si hay token+chat; email si hay
 * destinatario. Destinatarios vacíos se omiten sin fallar el job.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionEnvioService {

    private final DocumentoStoragePort documentoStorage;
    private final NotificacionProperties props;
    private final EmailNotificacionSender emailSender;
    private final TelegramNotificacionSender telegramSender;
    private final WhatsAppNotificacionSender whatsappSender;

    public void enviar(NotificacionMensaje msg) {
        byte[] pdf = msg.pdfUrl() != null
                ? documentoStorage.descargarPdf(msg.pdfUrl())
                : null;

        if (msg.paraEmail() != null && !msg.paraEmail().isBlank()) {
            emailSender.send(msg.paraEmail(), msg.asunto(), pdf, msg.pdfUrl());
        } else {
            log.debug("email omitido (sin destinatario) job_id={}", msg.jobId());
        }

        if (msg.paraWhatsapp() != null && !msg.paraWhatsapp().isBlank()) {
            whatsappSender.send(msg.paraWhatsapp(), msg.asunto(), pdf);
        }

        if (props.telegram() != null
                && props.telegram().botToken() != null
                && !props.telegram().botToken().isBlank()
                && props.telegram().chatId() != null
                && !props.telegram().chatId().isBlank()) {
            telegramSender.send(props.telegram().chatId(), msg.asunto(), pdf, msg.pdfUrl());
        }
    }
}
