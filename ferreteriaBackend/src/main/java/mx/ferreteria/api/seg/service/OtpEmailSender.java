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
        return mx.ferreteria.api.common.privacy.DatosSensibles.enmascararEmail(email);
    }

    /**
     * Aviso de takeover single-session: la sesión anterior se cerró porque el
     * usuario inició sesión en otro equipo. Mismo contrato que
     * {@link #enviarCodigo}: lanza SERVICIO_NO_DISPONIBLE si el SMTP falla; el
     * llamador (AuthService) lo trata como best-effort para no bloquear el
     * login cuando el correo cae.
     *
     * @param ubicacionNueva ciudad/región/país del nuevo inicio, o null si no
     *        se resolvió (no se menciona en ese caso).
     */
    public void enviarAvisoSesionCerrada(String to, String ubicacionNueva) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Tu sesión se cerró — " + EmailPlantilla.MARCA);
            helper.setText(textoPlanoAviso(ubicacionNueva), htmlAviso(ubicacionNueva));
            mailSender.send(message);
            log.info("aviso sesion cerrada enviado to={}", enmascarar(to));
        } catch (Exception e) {
            log.warn("aviso sesion email fallo to={} err={}", enmascarar(to), e.getMessage());
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
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

    private static String textoPlanoAviso(String ubicacionNueva) {
        return "Hola,\n\n"
                + "Tu sesión de " + EmailPlantilla.MARCA + " se cerró porque iniciaste "
                + "sesión en otro equipo"
                + (ubicacionNueva == null || ubicacionNueva.isBlank()
                        ? ".\n\n" : " desde " + ubicacionNueva + ".\n\n")
                + "Si fuiste tú, ignora este mensaje. Si no fuiste tú, cambia tu "
                + "contraseña de inmediato y avisa a tu administrador.\n\n"
                + "— " + EmailPlantilla.MARCA;
    }

    private static String htmlAviso(String ubicacionNueva) {
        String filas = EmailPlantilla.fila("Motivo", "Nuevo inicio de sesión en otro equipo")
                + ((ubicacionNueva == null || ubicacionNueva.isBlank()) ? ""
                        : EmailPlantilla.fila("Ubicación aproximada", ubicacionNueva));
        String bloque = EmailPlantilla.detalles(filas);
        return EmailPlantilla.documento(
                "Tu sesi&oacute;n anterior se cerr&oacute;:",
                "Detectamos un nuevo inicio de sesi&oacute;n con tu usuario en otro "
                        + "equipo, así que cerramos la sesi&oacute;n anterior para mantener "
                        + "una sola sesi&oacute;n activa.",
                bloque,
                "Si fuiste t&uacute;, ignora este mensaje. Si <strong>no</strong> fuiste "
                        + "t&uacute;, cambia tu contrase&ntilde;a de inmediato y avisa a tu "
                        + "administrador.");
    }
}
