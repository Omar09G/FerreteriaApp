package mx.ferreteria.api.notif.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;

/**
 * Envío multi-canal de un mensaje ya publicado. Cada canal activo se intenta
 * (email si hay destinatario, WhatsApp si hay número, Telegram si hay
 * token+chat; con los dos → por los dos) y el fallo de uno no salta los
 * demás. Semántica: si al menos un canal entregó → ENVIADA; si todos los
 * intentados fallaron → lanza y el job va a ERROR (reconciler reintenta).
 * Una omisión (canal deshabilitado, sin número) o un fallo reportado por el
 * sender (WhatsApp/Telegram nunca lanzan: devuelven false) no cuenta como
 * entrega. Así un reintento nunca duplica lo ya entregado por otro canal.
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

        int intentados = 0;
        int exitosos = 0;
        RuntimeException primerFallo = null;

        if (msg.paraEmail() != null && !msg.paraEmail().isBlank()) {
            intentados++;
            try {
                emailSender.send(msg.paraEmail(), msg.tipo(), msg.asunto(), msg.total(),
                        pdf, msg.pdfUrl());
                exitosos++;
            } catch (RuntimeException e) {
                primerFallo = e;
                log.warn("email fallo job_id={} err={}", msg.jobId(), e.getMessage());
            }
        } else {
            log.debug("email omitido (sin destinatario) job_id={}", msg.jobId());
        }

        if (msg.paraWhatsapp() != null && !msg.paraWhatsapp().isBlank()) {
            intentados++;
            try {
                if (whatsappSender.send(msg.paraWhatsapp(), msg.asunto(), pdf)) {
                    exitosos++;
                }
            } catch (RuntimeException e) {
                primerFallo = e;
                log.warn("whatsapp fallo job_id={} err={}", msg.jobId(), e.getMessage());
            }
        } else {
            log.debug("whatsapp omitido (sin destinatario) job_id={}", msg.jobId());
        }

        if (props.telegram() != null
                && props.telegram().botToken() != null
                && !props.telegram().botToken().isBlank()
                && props.telegram().chatId() != null
                && !props.telegram().chatId().isBlank()) {
            intentados++;
            try {
                if (telegramSender.send(props.telegram().chatId(), msg.asunto(), pdf, msg.pdfUrl())) {
                    exitosos++;
                }
            } catch (RuntimeException e) {
                primerFallo = e;
                log.warn("telegram fallo job_id={} err={}", msg.jobId(), e.getMessage());
            }
        }

        if (intentados > 0 && exitosos == 0) {
            throw primerFallo != null ? primerFallo
                    : new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }
}
