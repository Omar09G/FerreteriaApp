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
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse;
import mx.ferreteria.api.ven.service.CreditoService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CobranzaInformeServiceTest {

    @Mock
    CreditoService creditoService;
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

    private CobranzaInformeService service() {
        return new CobranzaInformeService(creditoService, destinatarioRepo,
                jobService, jobRepo, emailProvider, whatsappProvider);
    }

    private static CuentaCobrarResponse cuenta(String cliente, String saldo, LocalDate vto) {
        return new CuentaCobrarResponse(1L, 10L, "V-1", 5L, cliente,
                new BigDecimal("1000.00"), new BigDecimal("1000.00").subtract(new BigDecimal(saldo)),
                new BigDecimal(saldo), vto, "VIGENTE", java.time.Instant.now(), List.of());
    }

    private NotificacionJob job(String estado) {
        return NotificacionJob.builder().jobId(9L)
                .tipo(NotificacionJob.TIPO_COBRANZA)
                .refTipo(NotificacionJob.REF_COBRANZA).refId(1L)
                .estado(estado).build();
    }

    @Test
    @DisplayName("enviar con adeudos: separa vencidas/pendientes y notifica por ambos canales")
    void enviar_conAdeudos_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearCobranza(any())).thenReturn(job);
        when(creditoService.cuentasAbiertas()).thenReturn(List.of(
                cuenta("Cliente A", "500.00", LocalDate.now().minusDays(12)),
                cuenta("Cliente B", "300.00", LocalDate.now().plusDays(5))));
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
        assertThat(r.pendientes()).isEqualTo(1);
        assertThat(r.totalVencido()).isEqualByComparingTo("500.00");
        assertThat(r.totalPendiente()).isEqualByComparingTo("300.00");
        verify(emailSender).sendCobranza(eq("g@x.mx"), any(), any(), any());
        verify(whatsappSender).sendTexto(eq("5215500000001"), anyString());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin cuentas abiertas: no envía nada pero audita ENVIADA")
    void enviar_sinCuentas_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearCobranza(any())).thenReturn(job);
        when(creditoService.cuentasAbiertas()).thenReturn(List.of());

        var r = service().enviar();

        assertThat(r.emailsEnviados()).isZero();
        assertThat(r.whatsappsEnviados()).isZero();
        verify(emailSender, never()).sendCobranza(anyString(), any(), any(), any());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin destinatarios con correo: 422 COBRANZA_SIN_DESTINATARIOS")
    void enviar_sinDestinatarios_422() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearCobranza(any())).thenReturn(job);
        when(creditoService.cuentasAbiertas()).thenReturn(List.of(
                cuenta("Cliente A", "500.00", LocalDate.now().minusDays(3))));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.COBRANZA_SIN_DESTINATARIOS));
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("sin canales: 503 SERVICIO_NO_DISPONIBLE")
    void enviar_sinCanales_503() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearCobranza(any())).thenReturn(job);
        when(creditoService.cuentasAbiertas()).thenReturn(List.of(
                cuenta("Cliente A", "500.00", LocalDate.now().minusDays(3))));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(
                List.of(new DestinatarioInforme("g@x.mx", null)));
        when(emailProvider.getIfAvailable()).thenReturn(null);
        when(whatsappProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE));
    }

    @Test
    @DisplayName("estado sin job: yaEnviado=false; con job ENVIADA: true")
    void estado_ramos() {
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_COBRANZA), any()))
                .thenReturn(Optional.empty());
        assertThat(service().estado().yaEnviado()).isFalse();

        var enviado = job(NotificacionJob.ESTADO_ENVIADA);
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_COBRANZA), any()))
                .thenReturn(Optional.of(enviado));
        assertThat(service().estado().yaEnviado()).isTrue();
    }
}
