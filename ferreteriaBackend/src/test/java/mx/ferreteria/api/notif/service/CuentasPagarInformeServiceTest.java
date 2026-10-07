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

import mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse;
import mx.ferreteria.api.com.service.CompraService;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuentasPagarInformeServiceTest {

    @Mock
    CompraService compraService;
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

    private CuentasPagarInformeService service() {
        return new CuentasPagarInformeService(compraService, destinatarioRepo,
                jobService, jobRepo, emailProvider, whatsappProvider, bandejaService);
    }

    private static FacturaVencidaResponse vencida(String proveedor, String saldo, int dias) {
        return new FacturaVencidaResponse(1L, "C-1", "F-1", 2, proveedor, "555",
                LocalDate.of(2026, 9, 1), new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal(saldo), LocalDate.of(2026, 9, 20), dias, dias + " días", null);
    }

    private static FacturaPendienteResponse pendiente(String proveedor, String saldo) {
        return new FacturaPendienteResponse(2L, "C-2", "F-2", 3, proveedor,
                LocalDate.of(2026, 10, 1), new BigDecimal("800.00"), BigDecimal.ZERO,
                new BigDecimal(saldo), "VIGENTE", LocalDate.now().plusDays(5), 5, "por vencer",
                null);
    }

    private NotificacionJob job(String estado) {
        return NotificacionJob.builder().jobId(9L)
                .tipo(NotificacionJob.TIPO_CUENTAS_PAGAR)
                .refTipo(NotificacionJob.REF_CUENTAS).refId(1L)
                .estado(estado).build();
    }

    @Test
    @DisplayName("enviar con adeudos: correo + WhatsApp y job ENVIADA con totales")
    void enviar_conAdeudos_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearCuentasPagar(any())).thenReturn(job);
        when(compraService.facturasVencidas())
                .thenReturn(List.of(vencida("Proveedor A", "500.00", 12)));
        when(compraService.facturasPendientes())
                .thenReturn(List.of(pendiente("Proveedor B", "300.00")));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", null),
                new DestinatarioInforme("a@x.mx", "5215500000001"),
                new DestinatarioInforme(null, "5215500000002")));
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        when(whatsappProvider.getIfAvailable()).thenReturn(whatsappSender);
        when(whatsappSender.sendTexto(anyString(), anyString())).thenReturn(true);

        var r = service().enviar();

        assertThat(r.destinatarios()).isEqualTo(3);
        assertThat(r.emailsEnviados()).isEqualTo(2);
        assertThat(r.whatsappsEnviados()).isEqualTo(2);
        assertThat(r.vencidas()).isEqualTo(1);
        assertThat(r.pendientes()).isEqualTo(1);
        assertThat(r.totalVencido()).isEqualByComparingTo("500.00");
        assertThat(r.totalPendiente()).isEqualByComparingTo("300.00");
        verify(emailSender).sendCuentasPagar(eq("g@x.mx"), any(), any(), any());
        verify(emailSender).sendCuentasPagar(eq("a@x.mx"), any(), any(), any());
        verify(whatsappSender).sendTexto(eq("5215500000001"), anyString());
        verify(whatsappSender).sendTexto(eq("5215500000002"), anyString());
        verify(jobService).marcarProcesando(job);
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin adeudos: no envía correos pero audita el job como ENVIADA")
    void enviar_sinAdeudos_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearCuentasPagar(any())).thenReturn(job);
        when(compraService.facturasVencidas()).thenReturn(List.of());
        when(compraService.facturasPendientes()).thenReturn(List.of());

        var r = service().enviar();

        assertThat(r.emailsEnviados()).isZero();
        verify(emailSender, never()).sendCuentasPagar(anyString(), any(), any(), any());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin destinatarios con correo: 422 CUENTAS_SIN_DESTINATARIOS y job en ERROR")
    void enviar_sinDestinatarios_422() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearCuentasPagar(any())).thenReturn(job);
        when(compraService.facturasVencidas())
                .thenReturn(List.of(vencida("Proveedor A", "500.00", 3)));
        when(compraService.facturasPendientes()).thenReturn(List.of());
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.CUENTAS_SIN_DESTINATARIOS));
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("canales ausentes: 503 SERVICIO_NO_DISPONIBLE")
    void enviar_sinCanal_503() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearCuentasPagar(any())).thenReturn(job);
        when(compraService.facturasVencidas())
                .thenReturn(List.of(vencida("Proveedor A", "500.00", 3)));
        when(compraService.facturasPendientes()).thenReturn(List.of());
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
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_CUENTAS_PAGAR), any()))
                .thenReturn(Optional.empty());
        assertThat(service().estado().yaEnviado()).isFalse();

        var enviado = job(NotificacionJob.ESTADO_ENVIADA);
        enviado.setEnviadoEn(java.time.Instant.now());
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_CUENTAS_PAGAR), any()))
                .thenReturn(Optional.of(enviado));
        var est = service().estado();
        assertThat(est.yaEnviado()).isTrue();
        assertThat(est.estado()).isEqualTo(NotificacionJob.ESTADO_ENVIADA);
        assertThat(est.enviadoEn()).isNotNull();
    }
}
