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
import java.time.Instant;
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
import mx.ferreteria.api.fin.dto.FinDtos.TurnoCajaResponse;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnoAbiertoInformeServiceTest {

    @Mock
    CajaService cajaService;
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

    private TurnoAbiertoInformeService service() {
        return new TurnoAbiertoInformeService(cajaService, destinatarioRepo,
                jobService, jobRepo, emailProvider, whatsappProvider, bandejaService);
    }

    private static TurnoCajaResponse turno(Long id, String caja) {
        return new TurnoCajaResponse(id, 1, caja, 7, Instant.now().minusSeconds(3600),
                new BigDecimal("1000.00"), null, null, null, null, "ABIERTO", null);
    }

    private NotificacionJob job(String estado) {
        return NotificacionJob.builder().jobId(9L)
                .tipo(NotificacionJob.TIPO_TURNO_ABIERTO)
                .refTipo(NotificacionJob.REF_TURNO).refId(1L)
                .estado(estado).build();
    }

    @Test
    @DisplayName("enviar con turnos abiertos: avisa por ambos canales")
    void enviar_conTurnos_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearTurnoAbierto(any())).thenReturn(job);
        when(cajaService.turnosAbiertos()).thenReturn(List.of(
                turno(1L, "Caja 1"), turno(2L, "Caja 2")));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of(
                new DestinatarioInforme("g@x.mx", "5215500000001")));
        when(emailProvider.getIfAvailable()).thenReturn(emailSender);
        when(whatsappProvider.getIfAvailable()).thenReturn(whatsappSender);
        when(whatsappSender.sendTexto(anyString(), anyString())).thenReturn(true);

        var r = service().enviar();

        assertThat(r.destinatarios()).isEqualTo(1);
        assertThat(r.emailsEnviados()).isEqualTo(1);
        assertThat(r.whatsappsEnviados()).isEqualTo(1);
        assertThat(r.turnos()).isEqualTo(2);
        verify(emailSender).sendTurnoAbierto(eq("g@x.mx"), any(), any());
        verify(whatsappSender).sendTexto(eq("5215500000001"), anyString());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("todo cerrado: no envía nada pero audita ENVIADA")
    void enviar_sinTurnos_ok() {
        var job = job(NotificacionJob.ESTADO_PENDIENTE);
        when(jobService.crearTurnoAbierto(any())).thenReturn(job);
        when(cajaService.turnosAbiertos()).thenReturn(List.of());

        var r = service().enviar();

        assertThat(r.turnos()).isZero();
        assertThat(r.emailsEnviados()).isZero();
        verify(emailSender, never()).sendTurnoAbierto(anyString(), any(), any());
        verify(jobService).marcarEnviada(eq(job), eq(null));
    }

    @Test
    @DisplayName("sin destinatarios con correo: 422 TURNO_SIN_DESTINATARIOS")
    void enviar_sinDestinatarios_422() {
        var job = job(NotificacionJob.ESTADO_PROCESANDO);
        when(jobService.crearTurnoAbierto(any())).thenReturn(job);
        when(cajaService.turnosAbiertos()).thenReturn(List.of(turno(1L, "Caja 1")));
        when(destinatarioRepo.findGerentesYAdministradores()).thenReturn(List.of());

        assertThatThrownBy(() -> service().enviar())
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.TURNO_SIN_DESTINATARIOS));
        verify(jobService).marcarError(eq(job), anyString());
    }

    @Test
    @DisplayName("estado sin job: yaEnviado=false; con job ENVIADA: true")
    void estado_ramos() {
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_TURNO_ABIERTO), any()))
                .thenReturn(Optional.empty());
        assertThat(service().estado().yaEnviado()).isFalse();

        var enviado = job(NotificacionJob.ESTADO_ENVIADA);
        when(jobRepo.findByTipoAndRefId(eq(NotificacionJob.TIPO_TURNO_ABIERTO), any()))
                .thenReturn(Optional.of(enviado));
        assertThat(service().estado().yaEnviado()).isTrue();
    }
}
