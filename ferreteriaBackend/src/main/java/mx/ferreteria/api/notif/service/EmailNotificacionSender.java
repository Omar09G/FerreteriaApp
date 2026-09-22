package mx.ferreteria.api.notif.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Email con PDF adjunto (JavaMail). En dev apunta a Mailpit (:1025).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class EmailNotificacionSender {

    private final JavaMailSender mailSender;

    public void send(String to, String asunto, byte[] pdf, String clave) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(asunto != null ? asunto : "Notificación Ferretería");
            helper.setText("Adjuntamos el documento solicitado.", false);
            if (pdf != null && clave != null) {
                String nombre = clave.contains("/")
                        ? clave.substring(clave.lastIndexOf('/') + 1)
                        : clave;
                helper.addAttachment(nombre, new ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(message);
            log.info("email enviado to={}", to);
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }
}
