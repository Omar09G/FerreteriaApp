package mx.ferreteria.api.seg.service;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Correo del segundo factor (OTP). Componente propio —no depende de
 * {@code app.notif.enabled}— porque el login no puede quedar sin canal.
 * En dev apunta a Mailpit (:1025); en prod a SMTP real.
 *
 * <p>HTML con layout de tablas y CSS inline (compatible con Gmail, Outlook
 * y clientes móviles): encabezado de marca, código grande monoespaciado,
 * vigencia y aviso de seguridad, más versión en texto plano.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEmailSender {

    private static final String MARCA = "El Tornillo Feliz";
    private static final String COLOR_MARCA = "#c2410c";

    private final JavaMailSender mailSender;

    public void enviarCodigo(String to, String codigo, int ttlMinutos) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Tu código de verificación — " + MARCA);
            helper.setText(textoPlano(codigo, ttlMinutos), html(codigo, ttlMinutos));
            mailSender.send(message);
            log.info("otp enviado por email to={}", enmascarar(to));
        } catch (Exception e) {
            log.warn("otp email fallo to={} err={}", enmascarar(to), e.getMessage());
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    static String enmascarar(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String local = email.substring(0, email.indexOf('@'));
        String dominio = email.substring(email.indexOf('@'));
        String visible = local.length() <= 2 ? local.charAt(0) + "*"
                : local.substring(0, 2) + "***";
        return visible + dominio;
    }

    private static String textoPlano(String codigo, int ttlMinutos) {
        return "Hola,\n\n"
                + "Tu código de verificación de " + MARCA + " es:\n\n"
                + codigo + "\n\n"
                + "Vence en " + ttlMinutos + " minutos. Si no solicitaste este código, "
                + "ignora este mensaje y revisa la seguridad de tu cuenta.\n\n"
                + "— " + MARCA;
    }

    private static String html(String codigo, int ttlMinutos) {
        String digitos = new StringBuilder()
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">")
                .append("<tr>")
                .append("<td align=\"center\" style=\"background:#fff7ed;border:1px solid #fed7aa;")
                .append("border-radius:10px;padding:14px 28px;")
                .append("font-family:'Courier New',Courier,monospace;font-size:32px;font-weight:bold;")
                .append("letter-spacing:10px;color:#7c2d12;\">")
                .append(codigo)
                .append("</td></tr></table>")
                .toString();
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f5f1ea;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"520\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden; "
                + "border:1px solid #e7e0d3;\">"
                + "<tr><td align=\"center\" style=\"background:" + COLOR_MARCA + ";padding:22px 24px;\">"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;"
                + "color:#ffffff;\">&#x1F528; " + MARCA + "</div>"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#ffedd5; "
                + "margin-top:4px;\">Sistema de punto de venta</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 28px 8px 28px;font-family:Arial,Helvetica,sans-serif; "
                + "font-size:15px;color:#292524;\">"
                + "Hola,"
                + "</td></tr>"
                + "<tr><td style=\"padding:0 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px; "
                + "color:#292524;line-height:1.5;\">"
                + "Usa este c&oacute;digo para completar tu inicio de sesi&oacute;n:"
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:20px 28px;\">" + digitos + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:0 28px;font-family:Arial,Helvetica,sans-serif; "
                + "font-size:13px;color:#78716c;\">"
                + "Vence en <strong>" + ttlMinutos + " minutos</strong> y solo puede usarse una vez."
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 28px 0 28px;font-family:Arial,Helvetica,sans-serif; "
                + "font-size:13px;color:#78716c;line-height:1.5; "
                + "border-top:1px solid #f0ebe0;margin-top:20px;\">"
                + "Si no solicitaste este c&oacute;digo, ignora este mensaje y revisa "
                + "la seguridad de tu cuenta."
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:20px 28px 24px 28px; "
                + "font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#a8a29e;\">"
                + "Este es un mensaje autom&aacute;tico, no respondas a este correo.<br>"
                + "&copy; " + MARCA
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
