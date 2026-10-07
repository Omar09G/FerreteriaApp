package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.dto.VenDtos.RentaResponse;
import mx.ferreteria.api.ven.service.RentaService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentasInformeServiceTest {

    @Mock
    RentaService rentaService;
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
    @Mock
    BandejaService bandejaService;

    private RentasInformeService service() {
        return new RentasInformeService(rentaService, destinatarioRepo,
                jobService, jobRepo, emailProvider, whatsappProvider, bandejaService);
    }

    private static RentaResponse renta(String estado, LocalDate devEsperada) {
        return new RentaResponse(1L, "R-1", 5L, "Cliente A", 1, "Central",
                java.time.Instant.now(), devEsperada, null,
                new BigDecimal("200.00"), new BigDecimal("600.00"),
                1, null, estado, 7, List.of());
    }

    private NotificacionJob job(String estado) {
        return NotificacionJob.builder().jobId(9L)
                .tipo(NotificacionJob.TIPO_RENTAS)
                .refTipo(NotificacionJob.REF_RENTAS).refId(1L)
                .estado(estado).build();
    }

    @Test
    @DisplayName("enviar con vencidas y próximas: notifica por ambos canales")
    void enviar_conPendientes_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearRentas(any())).thenReturn(job);
        when(rentaService.rentasAbiertas()).thenReturn(List.of(
                renta("VENCIDA", LocalDate.now().minusDays(4)),
                renta("ABIERTA", LocalDate.now().plusDays(2)),
                renta("ABIERTA", LocalDate.now().plusDays(30))));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", "5215500000001")));
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        when(whatsappProvider.getIfAvailable()).thenReturn(whatsappSender);
        when(whatsappSender.sendTexto(anyString(), anyString())).thenReturn(true);

        var r = service().enviar();

        assertThat(r.destinatarios()).isEqualTo(1);
        assertThat(r.emailsEnviados()).isEqualTo(1);
        assertThat(r.whatsappsEnviados()).isEqualTo(1);
        assertThat(r.vencidas()).isEqualTo(1);
        assertThat(r.proximas()).isEqualTo(1);
        verify(emailSender).sendRentas(eq("g@x.mx"), any(), any(), any());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin rentas abiertas: no envía nada pero audita ENVIADA")
    void enviar_sinRentas_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearRentas(any())).thenReturn(job);
        when(rentaService.rentasAbiertas()).thenReturn(List.of());

        var r = service().enviar();

        assertThat(r.emailsEnviados()).isZero();
        verify(emailSender, never()).sendRentas(anyString(), any(), any(), any());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("ABIERTA lejana no entra en próximas (ventana 3 días)")
    void enviar_abiertaLejana_noAvisa() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearRentas(any())).thenReturn(job);
        when(rentaService.rentasAbiertas()).thenReturn(List.of(
                renta("ABIERTA", LocalDate.now().plusDays(30))));

        var r = service().enviar();

        assertThat(r.vencidas()).isZero();
        assertThat(r.proximas()).isZero();
        assertThat(r.emailsEnviados()).isZero();
    }

    @Test
    @DisplayName("sin destinatarios con correo: 422 RENTAS_SIN_DESTINATARIOS")
    void enviar_sinDestinatarios_422() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearRentas(any())).thenReturn(job);
        when(rentaService.rentasAbiertas()).thenReturn(List.of(
                renta("VENCIDA", LocalDate.now().minusDays(1))));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.RENTAS_SIN_DESTINATARIOS));
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("estado sin job: yaEnviado=false; con job ENVIADA: true")
    void estado_ramos() {
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_RENTAS), any()))
                .thenReturn(Optional.empty());
        assertThat(service().estado().yaEnviado()).isFalse();

        var enviado = job(NotificacionJob.ESTADO_ENVIADA);
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_RENTAS), any()))
                .thenReturn(Optional.of(enviado));
        assertThat(service().estado().yaEnviado()).isTrue();
    }
}
