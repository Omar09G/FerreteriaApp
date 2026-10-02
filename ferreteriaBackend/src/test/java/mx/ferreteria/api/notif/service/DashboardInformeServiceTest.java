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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.dto.ReportDtos;
import mx.ferreteria.api.ven.pdf.DashboardInformePdfService;
import mx.ferreteria.api.ven.service.ReporteService;

@ExtendWith(MockitoExtension.class)
class DashboardInformeServiceTest {

    @Mock
    ReporteService reporteService;
    @Mock
    DashboardInformePdfService pdfService;
    @Mock
    InformeDestinatarioRepository destinatarioRepo;
    @Mock
    NotificacionJobService jobService;
    @Mock
    NotificacionJobRepository jobRepo;
    @Mock
    DocumentoStoragePort documentoStorage;
    @Mock
    ObjectProvider<EmailNotificacionSender> emailProvider;
    @Mock
    ObjectProvider<WhatsAppNotificacionSender> whatsappProvider;
    @Mock
    EmailNotificacionSender emailSender;
    @Mock
    WhatsAppNotificacionSender whatsappSender;

    DashboardInformeService service;

    // Constructor manual: @InjectMocks no distingue los dos ObjectProvider
    // (mismo tipo tras erasure) y puede cablearlos cruzados.
    @BeforeEach
    void setUp() {
        service = new DashboardInformeService(reporteService, pdfService, destinatarioRepo,
                jobService, jobRepo, documentoStorage, emailProvider, whatsappProvider);
    }

    private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

    private ReportDtos.ResumenDashboardResponse resumen() {
        return new ReportDtos.ResumenDashboardResponse(
                new BigDecimal("15000.00"), 25L, new BigDecimal("600.00"),
                new BigDecimal("40000.00"), new BigDecimal("5000.00"),
                new BigDecimal("1800000.00"), 3L, 2L, 1L, 2L,
                new BigDecimal("150.00"));
    }

    private void stubBase(NotificacionJob job) {
        when(reporteService.resumenDashboard(HOY, HOY)).thenReturn(resumen());
        when(reporteService.cierreDiario(HOY, HOY)).thenReturn(List.of());
        when(pdfService.generarInformePdf(any(), any(), eq(HOY), eq(HOY)))
                .thenReturn(new byte[] { 1, 2, 3 });
        when(documentoStorage.subirPdf(anyString(), any())).thenReturn("informes/informe.pdf");
        when(jobService.crearInformeDashboard(HOY)).thenReturn(job);
    }

    @Test
    @DisplayName("envía por ambos canales y marca ENVIADA con conteos")
    void enviarInforme_ambosCanales_ok() {
        NotificacionJob job = NotificacionJob.builder()
                .jobId(1L).tipo(NotificacionJob.TIPO_INFORME_DASHBOARD)
                .estado(NotificacionJob.ESTADO_PENDIENTE).build();
        stubBase(job);
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", "5215500000001"),
                new DestinatarioInforme("a@x.mx", null)));
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        when(whatsappProvider.getIfAvailable()).thenReturn(whatsappSender);
        when(whatsappSender.send(anyString(), anyString(), any())).thenReturn(true);

        var r = service.enviarInforme(HOY, HOY);

        assertThat(r.destinatarios()).isEqualTo(2);
        assertThat(r.emailsEnviados()).isEqualTo(2);
        assertThat(r.whatsappEnviados()).isEqualTo(1);
        verify(jobService).marcarProcesando(job);
        verify(jobService).marcarEnviada(job, "informes/informe.pdf");
    }

    @Test
    @DisplayName("sin destinatarios -> 422 INFORME_SIN_DESTINATARIOS y sin job")
    void enviarInforme_sinDestinatarios_422() {
        when(reporteService.resumenDashboard(HOY, HOY)).thenReturn(resumen());
        when(reporteService.cierreDiario(HOY, HOY)).thenReturn(List.of());
        when(pdfService.generarInformePdf(any(), any(), eq(HOY), eq(HOY)))
                .thenReturn(new byte[] { 1 });
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service.enviarInforme(HOY, HOY))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INFORME_SIN_DESTINATARIOS);
        verify(jobService, never()).crearInformeDashboard(any());
    }

    @Test
    @DisplayName("cero entregas (canales ausentes) -> 503 y job en ERROR")
    void enviarInforme_sinEntregas_503() {
        NotificacionJob job = NotificacionJob.builder()
                .jobId(2L).tipo(NotificacionJob.TIPO_INFORME_DASHBOARD)
                .estado(NotificacionJob.ESTADO_PENDIENTE).build();
        stubBase(job);
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", "5215500000001")));
        when(emailProvider.getIfAvailable()).thenReturn(null);
        when(whatsappProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.enviarInforme(HOY, HOY))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE);
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("rango invertido -> 400 VALOR_INVALIDO")
    void enviarInforme_rangoInvalido_400() {
        assertThatThrownBy(() -> service.enviarInforme(HOY, HOY.minusDays(1)))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
    }

    @Test
    @DisplayName("estadoInforme true cuando el job del día está ENVIADA")
    void estadoInforme_enviada_true() {
        NotificacionJob job = NotificacionJob.builder()
                .jobId(3L).tipo(NotificacionJob.TIPO_INFORME_DASHBOARD)
                .estado(NotificacionJob.ESTADO_ENVIADA)
                .enviadoEn(java.time.Instant.now()).build();
        when(jobRepo.findByTipoAndRefId(NotificacionJob.TIPO_INFORME_DASHBOARD, HOY.toEpochDay()))
                .thenReturn(java.util.Optional.of(job));

        var r = service.estadoInforme(HOY, HOY);

        assertThat(r.yaEnviado()).isTrue();
        assertThat(r.estado()).isEqualTo(NotificacionJob.ESTADO_ENVIADA);
        assertThat(r.enviadoEn()).isNotNull();
    }

    @Test
    @DisplayName("estadoInforme false sin job o con ERROR (reintento directo)")
    void estadoInforme_sinJob_false() {
        when(jobRepo.findByTipoAndRefId(NotificacionJob.TIPO_INFORME_DASHBOARD, HOY.toEpochDay()))
                .thenReturn(java.util.Optional.empty());

        var r = service.estadoInforme(HOY, HOY);

        assertThat(r.yaEnviado()).isFalse();
        assertThat(r.estado()).isNull();
    }
}
