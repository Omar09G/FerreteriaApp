package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;
import mx.ferreteria.api.inv.service.InventarioService;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockBajoInformeServiceTest {

    @Mock
    InventarioService inventarioService;
    @Mock
    InformeDestinatarioRepository destinatarioRepo;
    @Mock
    NotificacionJobService jobService;
    @Mock
    NotificacionJobRepository jobRepo;
    @Mock
    ObjectProvider<EmailNotificacionSender> emailProvider;
    @Mock
    ObjectProvider<WhatsAppNotificacionSender> whatsappProvider;
    @Mock
    EmailNotificacionSender emailSender;
    @Mock
    WhatsAppNotificacionSender whatsappSender;

    private StockBajoInformeService service() {
        return new StockBajoInformeService(inventarioService, destinatarioRepo,
                jobService, jobRepo, emailProvider, whatsappProvider);
    }

    private static InventarioResponse fila(String codigo, String stock, String minimo) {
        return new InventarioResponse(1L, "Tornillo", codigo, 1, "Central",
                new BigDecimal(stock), new BigDecimal(minimo),
                new BigDecimal("100"), BigDecimal.ZERO);
    }

    private NotificacionJob job(String estado) {
        return NotificacionJob.builder().jobId(9L)
                .tipo(NotificacionJob.TIPO_STOCK_BAJO)
                .refTipo(NotificacionJob.REF_STOCK).refId(1L)
                .estado(estado).build();
    }

    @Test
    @DisplayName("enviar con bajo stock: Excel adjunto por correo y resumen por WhatsApp")
    void enviar_conRegistros_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearStockBajo(any())).thenReturn(job);
        when(inventarioService.bajoStockCompleto()).thenReturn(List.of(
                fila("T-1", "5", "10"), fila("T-2", "0", "10")));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", "5215500000001")));
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        when(whatsappProvider.getIfAvailable()).thenReturn(whatsappSender);
        when(whatsappSender.sendTexto(anyString(), anyString())).thenReturn(true);

        var r = service().enviar();

        assertThat(r.destinatarios()).isEqualTo(1);
        assertThat(r.emailsEnviados()).isEqualTo(1);
        assertThat(r.whatsappsEnviados()).isEqualTo(1);
        assertThat(r.productos()).isEqualTo(2);
        assertThat(r.agotados()).isEqualTo(1);
        assertThat(r.almacenes()).isEqualTo(1);
        verify(emailSender).sendStockBajo(eq("g@x.mx"), any(), eq(2), eq(1), eq(1),
                any(), anyString());
        verify(whatsappSender).sendTexto(eq("5215500000001"), anyString());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin bajo stock: no envía nada pero audita ENVIADA")
    void enviar_sinRegistros_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearStockBajo(any())).thenReturn(job);
        when(inventarioService.bajoStockCompleto()).thenReturn(List.of());

        var r = service().enviar();

        assertThat(r.emailsEnviados()).isZero();
        verify(emailSender, never()).sendStockBajo(anyString(), any(), anyInt(), anyInt(),
                anyInt(), any(), anyString());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin destinatarios con correo: 422 STOCK_SIN_DESTINATARIOS")
    void enviar_sinDestinatarios_422() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearStockBajo(any())).thenReturn(job);
        when(inventarioService.bajoStockCompleto())
                .thenReturn(List.of(fila("T-1", "2", "10")));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.STOCK_SIN_DESTINATARIOS));
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("estado sin job: yaEnviado=false; con job ENVIADA: true")
    void estado_ramos() {
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_STOCK_BAJO), any()))
                .thenReturn(Optional.empty());
        assertThat(service().estado().yaEnviado()).isFalse();

        var enviado = job(NotificacionJob.ESTADO_ENVIADA);
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_STOCK_BAJO), any()))
                .thenReturn(Optional.of(enviado));
        assertThat(service().estado().yaEnviado()).isTrue();
    }
}
