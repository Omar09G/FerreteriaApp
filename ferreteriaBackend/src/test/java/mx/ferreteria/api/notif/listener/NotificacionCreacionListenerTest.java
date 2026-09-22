package mx.ferreteria.api.notif.listener;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.service.NotificacionJobService;
import mx.ferreteria.api.notif.service.NotificacionService;
import mx.ferreteria.api.rh.service.NominaPagadaEvent;
import mx.ferreteria.api.ven.service.VentaCreadaEvent;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionCreacionListenerTest {

    @Mock
    NotificacionJobService jobService;
    @Mock
    NotificacionService notificacionService;

    @InjectMocks
    NotificacionCreacionListener listener;

    @Test
    @DisplayName("venta creada: crea job VENTA_TICKET y lo procesa")
    void onVentaCreada_creaYProcesa() {
        NotificacionJob job = NotificacionJob.builder().jobId(7L)
                .tipo(NotificacionJob.TIPO_VENTA_TICKET).refTipo(NotificacionJob.REF_VENTA)
                .refId(1L).estado(NotificacionJob.ESTADO_PENDIENTE).build();
        when(jobService.crearVentaTicket(1L)).thenReturn(job);

        listener.onVentaCreada(new VentaCreadaEvent(1L));

        verify(jobService).crearVentaTicket(1L);
        verify(notificacionService).procesar(7L);
    }

    @Test
    @DisplayName("nomina pagada: crea job NOMINA_PAGADA y lo procesa")
    void onNominaPagada_creaYProcesa() {
        NotificacionJob job = NotificacionJob.builder().jobId(8L)
                .tipo(NotificacionJob.TIPO_NOMINA_PAGADA).refTipo(NotificacionJob.REF_NOMINA)
                .refId(5L).estado(NotificacionJob.ESTADO_PENDIENTE).build();
        when(jobService.crearNominaPagada(5L)).thenReturn(job);

        listener.onNominaPagada(new NominaPagadaEvent(5L));

        verify(jobService).crearNominaPagada(5L);
        verify(notificacionService).procesar(8L);
    }

    @Test
    @DisplayName("fallo en crear: no propaga (no revierte la transacción de negocio)")
    void onVentaCreada_falloNoPropaga() {
        when(jobService.crearVentaTicket(anyLong()))
                .thenThrow(new RuntimeException("bd caída"));

        listener.onVentaCreada(new VentaCreadaEvent(1L));
    }
}
