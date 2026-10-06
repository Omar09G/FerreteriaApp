package mx.ferreteria.api.ven.service;

/**
 * Puerto de envío del ticket por WhatsApp. Vive en {@code ven} para no crear
 * un ciclo entre módulos (la implementación está en {@code notif}, que ya
 * depende de {@code ven}).
 */
public interface VentaTicketPort {

    /**
     * Genera el PDF del ticket y lo envía al número indicado.
     *
     * @throws mx.ferreteria.api.common.error.ApiException si no hay canal
     *         disponible o el proveedor rechaza el envío.
     */
    void enviarWhatsapp(Long ventaId, String telefono);
}
