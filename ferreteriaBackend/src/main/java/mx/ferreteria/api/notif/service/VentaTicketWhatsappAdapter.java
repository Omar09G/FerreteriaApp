package mx.ferreteria.api.notif.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.pdf.TicketPdfService;
import mx.ferreteria.api.ven.repo.VentaRepository;
import mx.ferreteria.api.ven.service.VentaTicketPort;

/**
 * Implementación del puerto de ticket por WhatsApp: genera el PDF al momento
 * y lo envía como documento. Sin proveedor (notif deshabilitado) o rechazo
 * del proveedor → SERVICIO_NO_DISPONIBLE (el POS lo muestra como error).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VentaTicketWhatsappAdapter implements VentaTicketPort {

    private final VentaRepository ventaRepo;
    private final TicketPdfService ticketPdfService;
    private final ObjectProvider<WhatsAppNotificacionSender> sender;

    @Override
    public void enviarWhatsapp(Long ventaId, String telefono) {
        var venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        var real = sender.getIfAvailable();
        if (real == null) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        byte[] pdf = ticketPdfService.generarTicketPdf(ventaId);
        String asunto = "Ticket " + venta.getFolio();
        if (!real.send(telefono, asunto, pdf)) {
            log.warn("ticket whatsapp rechazado venta_id={}", ventaId);
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        log.info("ticket whatsapp enviado venta_id={} folio={}", ventaId, venta.getFolio());
    }
}
