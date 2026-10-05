package mx.ferreteria.api.notif.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;

/**
 * Crea y transiciona jobs de notificación. Lo invoca el hook AFTER_COMMIT
 * del módulo (tras el commit de checkout / pagar), nunca los dominios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionJobService {

    private final NotificacionJobRepository repo;

    /** Idempotente: si ya existe (tipo, ref_id) devuelve el existente. */
    @Transactional
    public NotificacionJob crearVentaTicket(Long ventaId) {
        return crear(NotificacionJob.TIPO_VENTA_TICKET, NotificacionJob.REF_VENTA, ventaId);
    }

    @Transactional
    public NotificacionJob crearNominaPagada(Long nominaId) {
        return crear(NotificacionJob.TIPO_NOMINA_PAGADA, NotificacionJob.REF_NOMINA, nominaId);
    }

    /**
     * Job del informe diario: un registro por día (ref_id = epoch day del fin
     * del rango). Idempotente: los reenvíos del mismo día devuelven el
     * existente y actualizan su estado.
     */
    @Transactional
    public NotificacionJob crearInformeDashboard(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_INFORME_DASHBOARD, NotificacionJob.REF_INFORME,
                fecha.toEpochDay());
    }

    /**
     * Job del recordatorio de cuentas por pagar: un registro por día
     * (ref_id = epoch day). Idempotente como el del informe.
     */
    @Transactional
    public NotificacionJob crearCuentasPagar(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_CUENTAS_PAGAR, NotificacionJob.REF_CUENTAS,
                fecha.toEpochDay());
    }

    private NotificacionJob crear(String tipo, String refTipo, Long refId) {
        Optional<NotificacionJob> existente = repo.findByTipoAndRefId(tipo, refId);
        if (existente.isPresent()) {
            return existente.get();
        }
        NotificacionJob job = NotificacionJob.builder()
                .tipo(tipo)
                .refTipo(refTipo)
                .refId(refId)
                .estado(NotificacionJob.ESTADO_PENDIENTE)
                .intentos(0)
                .creadoEn(Instant.now())
                .build();
        // saveAndFlush: con IDENTITY el id debe quedar poblado ANTES de
        // devolver (el hook AFTER_COMMIT lo usa de inmediato para procesar).
        NotificacionJob saved = repo.saveAndFlush(job);
        log.info("notificacion_job creado job_id={} tipo={} ref={}/{}",
                saved.getJobId(), tipo, refTipo, refId);
        return saved;
    }

    @Transactional
    public void marcarProcesando(NotificacionJob job) {
        job.setEstado(NotificacionJob.ESTADO_PROCESANDO);
        job.setIntentos(job.getIntentos() == null ? 1 : job.getIntentos() + 1);
        job.setUltimoError(null);
        repo.save(job);
    }

    @Transactional
    public void marcarEnviada(NotificacionJob job, String pdfUrl) {
        job.setEstado(NotificacionJob.ESTADO_ENVIADA);
        job.setPdfUrl(pdfUrl);
        job.setEnviadoEn(Instant.now());
        job.setUltimoError(null);
        repo.save(job);
    }

    @Transactional
    public void marcarError(NotificacionJob job, String error) {
        job.setEstado(NotificacionJob.ESTADO_ERROR);
        job.setUltimoError(error != null && error.length() > 500
                ? error.substring(0, 500)
                : error);
        repo.save(job);
    }

    @Transactional
    public void guardarPdfUrl(NotificacionJob job, String pdfUrl) {
        job.setPdfUrl(pdfUrl);
        repo.save(job);
    }
}
