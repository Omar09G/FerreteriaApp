package mx.ferreteria.api.notif.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.seg.service.OtpWhatsappPort;

/**
 * Implementación del puerto OTP-WhatsApp: delega en
 * {@link WhatsAppNotificacionSender} cuando las notificaciones están
 * habilitadas; si no (dev/tests), captura en la bandeja mock para no dejar
 * el login sin canal observable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpWhatsappAdapter implements OtpWhatsappPort {

    private final NotificacionProperties props;
    private final WhatsAppMockBandeja bandeja;
    private final ObjectProvider<WhatsAppNotificacionSender> sender;

    @Override
    public boolean enviarTexto(String numero, String texto) {
        var real = sender.getIfAvailable();
        if (real != null) {
            return real.sendTexto(numero, texto);
        }
        var cfg = props.whatsapp();
        String prefijo = cfg == null ? "521" : cfg.prefijoPorDefecto();
        String digitos = numero == null ? "" : numero.replaceAll("\\D", "");
        if (digitos.length() == 10 && prefijo != null && !prefijo.isBlank()) {
            digitos = prefijo + digitos;
        }
        if (digitos.isBlank() || texto == null || texto.isBlank()) {
            return false;
        }
        bandeja.registrar(digitos, texto, null, null);
        log.info("otp whatsapp mock to={}", digitos);
        return true;
    }
}
