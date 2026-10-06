package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.pdf.TicketPdfService;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
class VentaTicketWhatsappAdapterTest {

    @Mock
    VentaRepository ventaRepo;
    @Mock
    TicketPdfService ticketPdfService;
    @Mock
    ObjectProvider<WhatsAppNotificacionSender> provider;
    @Mock
    WhatsAppNotificacionSender sender;

    private VentaTicketWhatsappAdapter adapter() {
        return new VentaTicketWhatsappAdapter(ventaRepo, ticketPdfService, provider);
    }

    private Venta venta() {
        return Venta.builder().ventaId(1L).folio("V-1").build();
    }

    @Test
    @DisplayName("venta existente + proveedor ok: genera PDF y lo envía")
    void enviar_ok() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(venta()));
        when(provider.getIfAvailable()).thenReturn(sender);
        when(ticketPdfService.generarTicketPdf(1L)).thenReturn(new byte[] { 1, 2, 3 });
        when(sender.send(eq("5215500000001"), eq("Ticket V-1"), any(byte[].class)))
                .thenReturn(true);

        adapter().enviarWhatsapp(1L, "5215500000001");

        verify(ticketPdfService).generarTicketPdf(1L);
        verify(sender).send(eq("5215500000001"), eq("Ticket V-1"), any(byte[].class));
    }

    @Test
    @DisplayName("venta inexistente: 404 RECURSO_NO_ENCONTRADO sin generar PDF")
    void ventaInexistente_404() {
        when(ventaRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter().enviarWhatsapp(99L, "5215500000001"))
                .isInstanceOf(
                        mx.ferreteria.api.common.error.RecursoNoEncontradoException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
        verify(ticketPdfService, never()).generarTicketPdf(99L);
    }

    @Test
    @DisplayName("sin proveedor: 503 SERVICIO_NO_DISPONIBLE")
    void sinProveedor_503() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(venta()));
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> adapter().enviarWhatsapp(1L, "5215500000001"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE));
    }

    @Test
    @DisplayName("proveedor rechaza: 503 SERVICIO_NO_DISPONIBLE")
    void rechazo_503() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(venta()));
        when(provider.getIfAvailable()).thenReturn(sender);
        when(ticketPdfService.generarTicketPdf(1L)).thenReturn(new byte[] { 1 });
        when(sender.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(false);

        assertThatThrownBy(() -> adapter().enviarWhatsapp(1L, "5215500000001"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE));
    }
}
