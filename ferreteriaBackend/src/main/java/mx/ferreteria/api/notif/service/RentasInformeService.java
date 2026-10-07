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
import mx.ferreteria.api.notif.dto.RentasDtos;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.dto.VenDtos.RentaResponse;
import mx.ferreteria.api.ven.service.RentaService;

/**
 * Recordatorio de rentas (vencidas + próximas a devolver en 3 días) a
 * GERENTES y ADMINISTRADORES: correo con detalle y WhatsApp con resumen.
 * Misma auditoría diaria que los demás recordatorios (tipo RENTAS). Solo se
 * notifica si hay registros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RentasInformeService {

    /** Ventana de "próxima a devolver" en días desde hoy. */
    static final int DIAS_PROXIMA = 3;

    private final RentaService rentaService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final ObjectProvider<EmailNotificacionSender> emailSender;
    private final ObjectProvider<WhatsAppNotificacionSender> whatsappSender;
    private final BandejaService bandejaService;

    @Transactional(readOnly = true)
    public RentasDtos.RentasEstadoResponse estado() {
        LocalDate hoy = ZonaHoraria.hoy();
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_RENTAS, hoy.toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new RentasDtos.RentasEstadoResponse(
                hoy, yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public RentasDtos.RentasEnvioResponse enviar() {
        LocalDate hoy = ZonaHoraria.hoy();
        LocalDate limite = hoy.plusDays(DIAS_PROXIMA);
        List<RentaResponse> abiertas = rentaService.rentasAbiertas();
        List<RentaResponse> vencidas = abiertas.stream()
                .filter(r -> "VENCIDA".equals(r.estado()))
                .toList();
        List<RentaResponse> proximas = abiertas.stream()
                .filter(r -> "ABIERTA".equals(r.estado())
                        && r.fechaDevEsperada() != null
                        && !r.fechaDevEsperada().isAfter(limite))
                .toList();

        NotificacionJob job = jobService.crearRentas(hoy);
        jobService.marcarProcesando(job);
        try {
            if (vencidas.isEmpty() && proximas.isEmpty()) {
                jobService.marcarEnviada(job, null);
                log.info("rentas sin pendientes fecha={}", hoy);
                return new RentasDtos.RentasEnvioResponse(hoy, 0, 0, 0, 0, 0);
            }
            List<DestinatarioInforme> destinatarios = destinatarioRepo
                    .findGerentesYAdministradores();
            if (destinatarios.stream().noneMatch(d -> d.email() != null && !d.email().isBlank())) {
                throw new ValidacionException(ErrorCode.RENTAS_SIN_DESTINATARIOS);
            }
            EmailNotificacionSender email = emailSender.getIfAvailable();
            WhatsAppNotificacionSender whatsapp = whatsappSender.getIfAvailable();
            if (email == null && whatsapp == null) {
                jobService.marcarError(job, "sin canales disponibles");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            int emails = 0;
            int whatsapps = 0;
            String resumen = resumenWhatsApp(vencidas, proximas);
            for (DestinatarioInforme d : destinatarios) {
                if (email != null && d.email() != null && !d.email().isBlank()) {
                    try {
                        email.sendRentas(d.email(), hoy, vencidas, proximas);
                        emails++;
                    } catch (RuntimeException e) {
                        log.warn("rentas email fallo to={} err={}", d.email(), e.getMessage());
                    }
                }
                if (whatsapp != null && d.whatsapp() != null && !d.whatsapp().isBlank()) {
                    try {
                        if (whatsapp.sendTexto(d.whatsapp(), resumen)) {
                            whatsapps++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("rentas whatsapp fallo err={}", e.getMessage());
                    }
                }
            }
            if (emails == 0 && whatsapps == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, null);
            bandejaService.publicarParaGerencia(NotificacionBandeja.TIPO_RENTAS,
                    NotificacionBandeja.REF_RENTAS, hoy.toEpochDay(),
                    "Rentas: " + vencidas.size() + " vencidas",
                    resumenWhatsApp(vencidas, proximas));
            log.info("rentas enviadas fecha={} destinatarios={} emails={} whatsapps={} "
                    + "vencidas={} proximas={}",
                    hoy, destinatarios.size(), emails, whatsapps,
                    vencidas.size(), proximas.size());
            return new RentasDtos.RentasEnvioResponse(hoy, destinatarios.size(),
                    emails, whatsapps, vencidas.size(), proximas.size());
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }

    static String resumenWhatsApp(List<RentaResponse> vencidas, List<RentaResponse> proximas) {
        return "El Tornillo Feliz — Rentas: " + vencidas.size() + " vencidas · "
                + proximas.size() + " próximas a devolver. Revise Ventas → Rentas.";
    }
}
