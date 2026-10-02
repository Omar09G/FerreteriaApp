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
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.common.web.RangoFechas;
import mx.ferreteria.api.notif.dto.InformeDtos;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;
import mx.ferreteria.api.ven.pdf.DashboardInformePdfService;
import mx.ferreteria.api.ven.service.ReporteService;

/**
 * Orquestador único del informe diario (botón manual + JOB programado):
 * lee dashboard + cierre de BD, genera el PDF, lo sube a storage y lo envía
 * por cada canal disponible (correo si hay email, WhatsApp si hay número).
 * Auditoría en {@code notif.notificacion_jobs} tipo INFORME_DASHBOARD, un
 * registro por día (ref_id = epoch day): los reenvíos del mismo día
 * actualizan el mismo registro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardInformeService {

    private final ReporteService reporteService;
    private final DashboardInformePdfService pdfService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final DocumentoStoragePort documentoStorage;
    private final ObjectProvider<EmailNotificacionSender> emailSender;
    private final ObjectProvider<WhatsAppNotificacionSender> whatsappSender;

    @Transactional(readOnly = true)
    public InformeDtos.InformeEstadoResponse estadoInforme(LocalDate fechaInicio, LocalDate fechaFin) {
        RangoFechas rango = RangoFechas.of(fechaInicio, fechaFin);
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_INFORME_DASHBOARD, rango.fin().toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new InformeDtos.InformeEstadoResponse(
                rango.inicio(), rango.fin(), yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public InformeDtos.InformeEnvioResponse enviarInforme(LocalDate fechaInicio, LocalDate fechaFin) {
        RangoFechas rango = RangoFechas.of(fechaInicio, fechaFin);
        var resumen = reporteService.resumenDashboard(rango.inicio(), rango.fin());
        var cierres = reporteService.cierreDiario(rango.inicio(), rango.fin());
        byte[] pdf = pdfService.generarInformePdf(resumen, cierres, rango.inicio(), rango.fin());

        List<DestinatarioInforme> destinatarios = destinatarioRepo.findGerentesYAdministradores();
        if (destinatarios.isEmpty()) {
            throw new ValidacionException(ErrorCode.INFORME_SIN_DESTINATARIOS);
        }

        String clave = "informes/informe-dashboard-" + rango.fin() + ".pdf";
        String pdfUrl = documentoStorage.subirPdf(clave, pdf);
        String asunto = "Informe diario Ferreteria - " + rango.fin();

        NotificacionJob job = jobService.crearInformeDashboard(rango.fin());
        jobService.marcarProcesando(job);
        try {
            int emails = 0;
            int whatsapps = 0;
            EmailNotificacionSender email = emailSender.getIfAvailable();
            WhatsAppNotificacionSender whatsapp = whatsappSender.getIfAvailable();
            for (DestinatarioInforme d : destinatarios) {
                if (d.email() != null && !d.email().isBlank() && email != null) {
                    try {
                        email.send(d.email(), asunto, pdf, clave);
                        emails++;
                    } catch (RuntimeException e) {
                        log.warn("informe email fallo to={} err={}", d.email(), e.getMessage());
                    }
                }
                if (d.whatsapp() != null && !d.whatsapp().isBlank() && whatsapp != null) {
                    try {
                        if (whatsapp.send(d.whatsapp(), asunto, pdf)) {
                            whatsapps++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("informe whatsapp fallo err={}", e.getMessage());
                    }
                }
            }
            if (emails == 0 && whatsapps == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, pdfUrl);
            log.info("informe diario enviado rango={}/{} destinatarios={} emails={} whatsapps={}",
                    rango.inicio(), rango.fin(), destinatarios.size(), emails, whatsapps);
            return new InformeDtos.InformeEnvioResponse(
                    rango.inicio(), rango.fin(), destinatarios.size(), emails, whatsapps);
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }
}
