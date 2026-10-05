package mx.ferreteria.api.notif.service;

import java.math.BigDecimal;
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
import mx.ferreteria.api.notif.dto.CobranzaDtos;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse;
import mx.ferreteria.api.ven.service.CreditoService;

/**
 * Recordatorio de cobranza (cuentas vencidas + pendientes) a GERENTES y
 * ADMINISTRADORES: correo con detalle y WhatsApp con resumen. Misma
 * auditoría diaria que los demás recordatorios (tipo COBRANZA). Solo se
 * notifica si hay registros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CobranzaInformeService {

    private final CreditoService creditoService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final ObjectProvider<EmailNotificacionSender> emailSender;
    private final ObjectProvider<WhatsAppNotificacionSender> whatsappSender;

    @Transactional(readOnly = true)
    public CobranzaDtos.CobranzaEstadoResponse estado() {
        LocalDate hoy = ZonaHoraria.hoy();
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_COBRANZA, hoy.toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new CobranzaDtos.CobranzaEstadoResponse(
                hoy, yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public CobranzaDtos.CobranzaEnvioResponse enviar() {
        LocalDate hoy = ZonaHoraria.hoy();
        List<CuentaCobrarResponse> abiertas = creditoService.cuentasAbiertas();
        List<CuentaCobrarResponse> vencidas = abiertas.stream()
                .filter(c -> c.fechaVencimiento() != null && c.fechaVencimiento().isBefore(hoy))
                .toList();
        List<CuentaCobrarResponse> pendientes = abiertas.stream()
                .filter(c -> !vencidas.contains(c))
                .toList();

        NotificacionJob job = jobService.crearCobranza(hoy);
        jobService.marcarProcesando(job);
        try {
            if (vencidas.isEmpty() && pendientes.isEmpty()) {
                jobService.marcarEnviada(job, null);
                log.info("cobranza sin adeudos fecha={}", hoy);
                return new CobranzaDtos.CobranzaEnvioResponse(hoy, 0, 0, 0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO);
            }
            List<DestinatarioInforme> destinatarios = destinatarioRepo
                    .findGerentesYAdministradores();
            if (destinatarios.stream().noneMatch(d -> d.email() != null && !d.email().isBlank())) {
                throw new ValidacionException(ErrorCode.COBRANZA_SIN_DESTINATARIOS);
            }
            EmailNotificacionSender email = emailSender.getIfAvailable();
            WhatsAppNotificacionSender whatsapp = whatsappSender.getIfAvailable();
            if (email == null && whatsapp == null) {
                jobService.marcarError(job, "sin canales disponibles");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            int emails = 0;
            int whatsapps = 0;
            String resumen = resumenWhatsApp(vencidas, pendientes);
            for (DestinatarioInforme d : destinatarios) {
                if (email != null && d.email() != null && !d.email().isBlank()) {
                    try {
                        email.sendCobranza(d.email(), hoy, vencidas, pendientes);
                        emails++;
                    } catch (RuntimeException e) {
                        log.warn("cobranza email fallo to={} err={}", d.email(), e.getMessage());
                    }
                }
                if (whatsapp != null && d.whatsapp() != null && !d.whatsapp().isBlank()) {
                    try {
                        if (whatsapp.sendTexto(d.whatsapp(), resumen)) {
                            whatsapps++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("cobranza whatsapp fallo err={}", e.getMessage());
                    }
                }
            }
            if (emails == 0 && whatsapps == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, null);
            log.info("cobranza enviada fecha={} destinatarios={} emails={} whatsapps={} "
                    + "vencidas={} pendientes={}",
                    hoy, destinatarios.size(), emails, whatsapps,
                    vencidas.size(), pendientes.size());
            return new CobranzaDtos.CobranzaEnvioResponse(hoy, destinatarios.size(),
                    emails, whatsapps, vencidas.size(), pendientes.size(),
                    total(vencidas), total(pendientes));
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }

    private static BigDecimal total(List<CuentaCobrarResponse> cuentas) {
        return cuentas.stream()
                .map(c -> c.saldo() == null ? BigDecimal.ZERO : c.saldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String resumenWhatsApp(List<CuentaCobrarResponse> vencidas,
            List<CuentaCobrarResponse> pendientes) {
        return "El Tornillo Feliz — Cobranza: " + vencidas.size() + " vencidas ("
                + EmailNotificacionSender.moneda(total(vencidas)) + ") · "
                + pendientes.size() + " pendientes ("
                + EmailNotificacionSender.moneda(total(pendientes))
                + "). Revise Ventas → Cobranza.";
    }
}
