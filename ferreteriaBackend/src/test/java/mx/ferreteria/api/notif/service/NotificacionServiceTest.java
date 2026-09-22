package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.rh.service.EmpleadoGateway.EmpleadoRow;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.pdf.TicketPdfService;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionServiceTest {

    @Mock
    NotificacionJobRepository jobRepo;
    @Mock
    NotificacionJobService jobService;
    @Mock
    VentaRepository ventaRepo;
    @Mock
    NominaRepository nominaRepo;
    @Mock
    ClienteRepository clienteRepo;
    @Mock
    EmpleadoGateway empleadoGateway;
    @Mock
    TicketPdfService ticketPdfService;
    @Mock
    NominaPdfService nominaPdfService;
    @Mock
    DocumentoStoragePort documentoStorage;
    @Mock
    NotificacionPublisher publisher;

    @InjectMocks
    NotificacionService service;

    private static final byte[] PDF = new byte[] { 0x25, 0x50, 0x44, 0x46 };

    private static NotificacionJob job(Long jobId, String tipo, String refTipo, Long refId, String estado) {
        return NotificacionJob.builder().jobId(jobId).tipo(tipo).refTipo(refTipo)
                .refId(refId).estado(estado).intentos(0).build();
    }

    @Test
    @DisplayName("VENTA_TICKET ok: genera PDF, sube, refleja pdf_url en venta y marca ENVIADA")
    void ventaTicket_ok() {
        NotificacionJob j = job(7L, NotificacionJob.TIPO_VENTA_TICKET,
                NotificacionJob.REF_VENTA, 1L, NotificacionJob.ESTADO_PENDIENTE);
        Venta v = Venta.builder().ventaId(1L).folio("V-1").clienteId(9L)
                .total(new BigDecimal("116.00")).build();
        Cliente c = Cliente.builder().clienteId(9L).razonSocial("ACME")
                .email("cte@acme.mx").whatsapp("5550001111").build();
        when(jobRepo.findById(7L)).thenReturn(Optional.of(j));
        when(ticketPdfService.generarTicketPdf(1L)).thenReturn(PDF);
        when(documentoStorage.subirPdf(eq("tickets/1.pdf"), eq(PDF))).thenReturn("tickets/1.pdf");
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(clienteRepo.findById(9L)).thenReturn(Optional.of(c));
        when(publisher.publicar(eq(j), any(NotificacionMensaje.class))).thenReturn(true);

        service.procesar(7L);

        verify(jobService).marcarProcesando(j);
        verify(jobService).guardarPdfUrl(j, "tickets/1.pdf");
        assertThat(v.getPdfUrl()).isEqualTo("tickets/1.pdf");
        verify(ventaRepo).save(v);
        ArgumentCaptor<NotificacionMensaje> cap = ArgumentCaptor.forClass(NotificacionMensaje.class);
        verify(publisher).publicar(eq(j), cap.capture());
        assertThat(cap.getValue().paraEmail()).isEqualTo("cte@acme.mx");
        assertThat(cap.getValue().paraWhatsapp()).isEqualTo("5550001111");
        verify(jobService).marcarEnviada(j, "tickets/1.pdf");
    }

    @Test
    @DisplayName("NOMINA_PAGADA ok: destinatario sale del empleado (email/whatsapp)")
    void nominaPagada_destinatarioEmpleado() {
        NotificacionJob j = job(8L, NotificacionJob.TIPO_NOMINA_PAGADA,
                NotificacionJob.REF_NOMINA, 5L, NotificacionJob.ESTADO_PENDIENTE);
        Nomina n = Nomina.builder().nominaId(5L).empleadoId(7)
                .periodoIni(LocalDate.of(2026, 1, 1)).periodoFin(LocalDate.of(2026, 1, 15))
                .netoPagar(new BigDecimal("5200.00")).build();
        EmpleadoRow row = new EmpleadoRow(7, 1, "Vendedor", "Juan", "Pérez", null, null, null,
                "555", "5559990001", "juan@x.mx", null, null, null, null, null, null,
                BigDecimal.ZERO, true, null);
        when(jobRepo.findById(8L)).thenReturn(Optional.of(j));
        when(nominaPdfService.generarNominaPdf(5L)).thenReturn(PDF);
        when(documentoStorage.subirPdf(eq("nominas/5.pdf"), eq(PDF))).thenReturn("nominas/5.pdf");
        when(nominaRepo.findById(5L)).thenReturn(Optional.of(n));
        when(empleadoGateway.findById(7)).thenReturn(Optional.of(row));
        when(publisher.publicar(eq(j), any(NotificacionMensaje.class))).thenReturn(true);

        service.procesar(8L);

        ArgumentCaptor<NotificacionMensaje> cap = ArgumentCaptor.forClass(NotificacionMensaje.class);
        verify(publisher).publicar(eq(j), cap.capture());
        assertThat(cap.getValue().paraEmail()).isEqualTo("juan@x.mx");
        assertThat(cap.getValue().paraWhatsapp()).isEqualTo("5559990001");
        verify(jobService).marcarEnviada(j, "nominas/5.pdf");
    }

    @Test
    @DisplayName("broker caído: job queda en ERROR para el reconciler, no ENVIADA")
    void brokerCaido_marcaError() {
        NotificacionJob j = job(7L, NotificacionJob.TIPO_VENTA_TICKET,
                NotificacionJob.REF_VENTA, 1L, NotificacionJob.ESTADO_PENDIENTE);
        Venta v = Venta.builder().ventaId(1L).folio("V-1").total(BigDecimal.ONE).build();
        when(jobRepo.findById(7L)).thenReturn(Optional.of(j));
        when(ticketPdfService.generarTicketPdf(1L)).thenReturn(PDF);
        when(documentoStorage.subirPdf(eq("tickets/1.pdf"), eq(PDF))).thenReturn("tickets/1.pdf");
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(clienteRepo.findById(any())).thenReturn(Optional.empty());
        when(publisher.publicar(eq(j), any(NotificacionMensaje.class))).thenReturn(false);

        service.procesar(7L);

        verify(jobService).marcarError(eq(j), any(String.class));
        verify(jobService, never()).marcarEnviada(any(), any());
    }

    @Test
    @DisplayName("job ya ENVIADA: no se reprocesa (idempotente)")
    void yaEnviada_noReprocesa() {
        NotificacionJob j = job(7L, NotificacionJob.TIPO_VENTA_TICKET,
                NotificacionJob.REF_VENTA, 1L, NotificacionJob.ESTADO_ENVIADA);
        when(jobRepo.findById(7L)).thenReturn(Optional.of(j));

        service.procesar(7L);

        verifyNoInteractions(documentoStorage, publisher);
        verify(jobService, never()).marcarProcesando(any());
    }
}
