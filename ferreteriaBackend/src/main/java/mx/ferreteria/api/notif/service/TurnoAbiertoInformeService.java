package mx.ferreteria.api.notif.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.fin.dto.FinDtos.TurnoCajaResponse;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.notif.dto.TurnoAbiertoDtos;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

/**
 * Aviso de turnos abiertos (corte sin cerrar) a GERENTES y ADMINISTRADORES:
 * correo con detalle y WhatsApp con resumen. Misma auditoría diaria que los
 * demás recordatorios (tipo TURNO_ABIERTO). Solo se notifica si hay turnos
 * abiertos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurnoAbiertoInformeService {

    private final CajaService cajaService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final ObjectProvider<EmailNotificacionSender> emailSender;
    private final ObjectProvider<WhatsAppNotificacionSender> whatsappSender;

    @Transactional(readOnly = true)
    public TurnoAbiertoDtos.TurnoAbiertoEstadoResponse estado() {
        LocalDate hoy = ZonaHoraria.hoy();
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_TURNO_ABIERTO, hoy.toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new TurnoAbiertoDtos.TurnoAbiertoEstadoResponse(
                hoy, yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public TurnoAbiertoDtos.TurnoAbiertoEnvioResponse enviar() {
        LocalDate hoy = ZonaHoraria.hoy();
        List<TurnoCajaResponse> turnos = cajaService.turnosAbiertos();

        NotificacionJob job = jobService.crearTurnoAbierto(hoy);
        jobService.marcarProcesando(job);
        try {
            if (turnos.isEmpty()) {
                jobService.marcarEnviada(job, null);
                log.info("turnos todos cerrados fecha={}", hoy);
                return new TurnoAbiertoDtos.TurnoAbiertoEnvioResponse(hoy, 0, 0, 0, 0);
            }
            List<DestinatarioInforme> destinatarios = destinatarioRepo
                    .findGerentesYAdministradores();
            if (destinatarios.stream().noneMatch(d -> d.email() != null && !d.email().isBlank())) {
                throw new ValidacionException(ErrorCode.TURNO_SIN_DESTINATARIOS);
            }
            EmailNotificacionSender email = emailSender.getIfAvailable();
            WhatsAppNotificacionSender whatsapp = whatsappSender.getIfAvailable();
            if (email == null && whatsapp == null) {
                jobService.marcarError(job, "sin canales disponibles");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            int emails = 0;
            int whatsapps = 0;
            String resumen = resumenWhatsApp(turnos);
            for (DestinatarioInforme d : destinatarios) {
                if (email != null && d.email() != null && !d.email().isBlank()) {
                    try {
                        email.sendTurnoAbierto(d.email(), hoy, turnos);
                        emails++;
                    } catch (RuntimeException e) {
                        log.warn("turnos email fallo to={} err={}",
                                mx.ferreteria.api.common.privacy.DatosSensibles
                                        .enmascararEmail(d.email()),
                                e.getMessage());
                    }
                }
                if (whatsapp != null && d.whatsapp() != null && !d.whatsapp().isBlank()) {
                    try {
                        if (whatsapp.sendTexto(d.whatsapp(), resumen)) {
                            whatsapps++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("turnos whatsapp fallo err={}", e.getMessage());
                    }
                }
            }
            if (emails == 0 && whatsapps == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, null);
            log.info("turnos avisados fecha={} destinatarios={} emails={} whatsapps={} turnos={}",
                    hoy, destinatarios.size(), emails, whatsapps, turnos.size());
            return new TurnoAbiertoDtos.TurnoAbiertoEnvioResponse(hoy, destinatarios.size(),
                    emails, whatsapps, turnos.size());
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }

    static String resumenWhatsApp(List<TurnoCajaResponse> turnos) {
        String cajas = turnos.stream()
                .map(t -> t.cajaNombre() == null ? "—" : t.cajaNombre())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return "El Tornillo Feliz — Corte pendiente: " + turnos.size()
                + (turnos.size() == 1 ? " turno abierto en " : " turnos abiertos en ")
                + cajas + ". Realice los cortes en Caja.";
    }
}
