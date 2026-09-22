package mx.ferreteria.api.notif.service;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.pdf.TicketPdfService;
import mx.ferreteria.api.ven.repo.VentaRepository;

/**
 * Servicio de dominio de notificaciones: orquesta el trabajo de un job
 * (genera PDF, sube a storage, refleja la clave en ven.ventas para tickets,
 * arma el mensaje y publica a RabbitMQ). Idempotente: un job ENVIADA no se
 * reprocesa; si el broker no acepta, el job queda en ERROR para el reconciler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionService {

    private final NotificacionJobRepository jobRepo;
    private final NotificacionJobService jobService;
    private final VentaRepository ventaRepo;
    private final NominaRepository nominaRepo;
    private final ClienteRepository clienteRepo;
    private final EmpleadoGateway empleadoGateway;
    private final TicketPdfService ticketPdfService;
    private final NominaPdfService nominaPdfService;
    private final DocumentoStoragePort documentoStorage;
    private final NotificacionPublisher publisher;

    @Transactional
    public void procesar(Long jobId) {
        NotificacionJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado())) {
            return;
        }
        jobService.marcarProcesando(job);
        try {
            String clave = generarYSubir(job);
            jobService.guardarPdfUrl(job, clave);
            if (NotificacionJob.TIPO_VENTA_TICKET.equals(job.getTipo())) {
                ventaRepo.findById(job.getRefId()).ifPresent(v -> {
                    v.setPdfUrl(clave);
                    ventaRepo.save(v);
                });
            }
            NotificacionMensaje mensaje = armarMensaje(job, clave);
            if (publisher.publicar(job, mensaje)) {
                jobService.marcarEnviada(job, clave);
            } else {
                jobService.marcarError(job, "broker no disponible");
            }
        } catch (Exception e) {
            log.warn("fallo procesar job_id={} err={}", jobId, e.getMessage());
            jobService.marcarError(job, e.getMessage());
        }
    }

    private String generarYSubir(NotificacionJob job) {
        if (NotificacionJob.TIPO_VENTA_TICKET.equals(job.getTipo())) {
            byte[] pdf = ticketPdfService.generarTicketPdf(job.getRefId());
            return documentoStorage.subirPdf("tickets/" + job.getRefId() + ".pdf", pdf);
        }
        if (NotificacionJob.TIPO_NOMINA_PAGADA.equals(job.getTipo())) {
            byte[] pdf = nominaPdfService.generarNominaPdf(job.getRefId());
            return documentoStorage.subirPdf("nominas/" + job.getRefId() + ".pdf", pdf);
        }
        throw new ValidacionException(ErrorCode.VALOR_INVALIDO, job.getTipo());
    }

    private NotificacionMensaje armarMensaje(NotificacionJob job, String clave) {
        if (NotificacionJob.TIPO_VENTA_TICKET.equals(job.getTipo())) {
            Venta v = ventaRepo.findById(job.getRefId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
            String email = null;
            String whatsapp = null;
            if (v.getClienteId() != null) {
                Optional<Cliente> c = clienteRepo.findById(v.getClienteId());
                if (c.isPresent()) {
                    email = c.get().getEmail();
                    whatsapp = c.get().getWhatsapp();
                }
            }
            return new NotificacionMensaje(
                    job.getJobId(), job.getTipo(), job.getRefTipo(), job.getRefId(),
                    clave, email, whatsapp,
                    "Ticket " + v.getFolio(), v.getTotal());
        }
        Nomina n = nominaRepo.findById(job.getRefId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        String email = null;
        String whatsapp = null;
        var emp = empleadoGateway.findById(n.getEmpleadoId()).orElse(null);
        if (emp != null) {
            email = emp.email();
            whatsapp = emp.whatsapp();
        }
        return new NotificacionMensaje(
                job.getJobId(), job.getTipo(), job.getRefTipo(), job.getRefId(),
                clave, email, whatsapp,
                "Nómina pagada " + n.getPeriodoIni() + " – " + n.getPeriodoFin(),
                n.getNetoPagar());
    }
}
