package mx.ferreteria.api.seg.service;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.mail.EmailPlantilla;

/**
 * Correo del segundo factor (OTP). Componente propio —no depende de
 * {@code app.notif.enabled}— porque el login no puede quedar sin canal.
 * En dev apunta a Mailpit (:1025); en prod a SMTP real.
 *
 * <p>Usa la plantilla compartida {@link EmailPlantilla}: encabezado de
 * marca, código grande monoespaciado, vigencia y aviso de seguridad, más
 * versión en texto plano.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEmailSender {

    private final JavaMailSender mailSender;

    public void enviarCodigo(String to, String codigo, int ttlMinutos) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Tu código de verificación — " + EmailPlantilla.MARCA);
            helper.setText(textoPlano(codigo, ttlMinutos), html(codigo, ttlMinutos));
            mailSender.send(message);
            log.info("otp enviado por email to={}", enmascarar(to));
        } catch (Exception e) {
            log.warn("otp email fallo to={} err={}", enmascarar(to), e.getMessage());
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    static String enmascarar(String email) {
        return EmailPlantilla.enmascararEmail(email);
    }

    private static String textoPlano(String codigo, int ttlMinutos) {
        return "Hola,\n\n"
                + "Tu código de verificación de " + EmailPlantilla.MARCA + " es:\n\n"
                + codigo + "\n\n"
                + "Vence en " + ttlMinutos + " minutos. Si no solicitaste este código, "
                + "ignora este mensaje y revisa la seguridad de tu cuenta.\n\n"
                + "— " + EmailPlantilla.MARCA;
    }

    private static String html(String codigo, int ttlMinutos) {
        String caja = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                + "<tr>"
                + "<td align=\"center\" style=\"background:#fff7ed;border:1px solid #fed7aa;"
                + "border-radius:10px;padding:14px 28px;"
                + "font-family:'Courier New',Courier,monospace;font-size:32px;font-weight:bold;"
                + "letter-spacing:10px;color:#7c2d12;\">"
                + EmailPlantilla.escapar(codigo)
                + "</td></tr></table>";
        return EmailPlantilla.documento(
                "Usa este c&oacute;digo para completar tu inicio de sesi&oacute;n:",
                "Tu c&oacute;digo de verificaci&oacute;n es:",
                caja,
                "Vence en <strong>" + ttlMinutos + " minutos</strong> y solo puede usarse "
                        + "una vez. Si no solicitaste este c&oacute;digo, ignora este mensaje "
                        + "y revisa la seguridad de tu cuenta.");
    }
}
