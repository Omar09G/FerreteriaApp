package mx.ferreteria.api.seg.service;

/**
 * Puerto de envío OTP por WhatsApp. Vive en {@code seg} para no crear un
 * ciclo entre módulos (la implementación está en {@code notif}, que ya
 * depende de {@code seg}).
 */
public interface OtpWhatsappPort {

    /**
     * Envía un texto corto (código OTP) al número indicado.
     * @return true si el proveedor lo aceptó (o el mock lo capturó).
     */
    boolean enviarTexto(String numero, String texto);
}
