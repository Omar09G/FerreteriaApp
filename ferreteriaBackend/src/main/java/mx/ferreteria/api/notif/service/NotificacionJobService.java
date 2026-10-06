package mx.ferreteria.api.notif.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;

/**
 * Crea y transiciona jobs de notificación. Lo invoca el hook AFTER_COMMIT
 * del módulo (tras el commit de checkout / pagar), nunca los dominios.
 *
 * <p>Todos los métodos son {@code REQUIRES_NEW}: los servicios de envío
 * hacen I/O externo (email/WhatsApp) dentro de su propia transacción y, si
 * esta hace rollback al fallar, la auditoría del job (procesando/enviada/
 * error) debe sobrevivir igual.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionJobService {

    private final NotificacionJobRepository repo;

    /** Idempotente: si ya existe (tipo, ref_id) devuelve el existente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearVentaTicket(Long ventaId) {
        return crear(NotificacionJob.TIPO_VENTA_TICKET, NotificacionJob.REF_VENTA, ventaId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearNominaPagada(Long nominaId) {
        return crear(NotificacionJob.TIPO_NOMINA_PAGADA, NotificacionJob.REF_NOMINA, nominaId);
    }

    /**
     * Job del informe diario: un registro por día (ref_id = epoch day del fin
     * del rango). Idempotente: los reenvíos del mismo día devuelven el
     * existente y actualizan su estado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearInformeDashboard(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_INFORME_DASHBOARD, NotificacionJob.REF_INFORME,
                fecha.toEpochDay());
    }

    /**
     * Job del recordatorio de cuentas por pagar: un registro por día
     * (ref_id = epoch day). Idempotente como el del informe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearCuentasPagar(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_CUENTAS_PAGAR, NotificacionJob.REF_CUENTAS,
                fecha.toEpochDay());
    }

    /** Job del recordatorio de cobranza: un registro por día. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearCobranza(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_COBRANZA, NotificacionJob.REF_COBRANZA,
                fecha.toEpochDay());
    }

    /** Job del recordatorio de rentas: un registro por día. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearRentas(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_RENTAS, NotificacionJob.REF_RENTAS,
                fecha.toEpochDay());
    }

    /** Job del recordatorio de stock bajo: un registro por día. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearStockBajo(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_STOCK_BAJO, NotificacionJob.REF_STOCK,
                fecha.toEpochDay());
    }

    /** Job del aviso de turnos abiertos: un registro por día. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificacionJob crearTurnoAbierto(java.time.LocalDate fecha) {
        return crear(NotificacionJob.TIPO_TURNO_ABIERTO, NotificacionJob.REF_TURNO,
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarProcesando(NotificacionJob job) {
        job.setEstado(NotificacionJob.ESTADO_PROCESANDO);
        job.setIntentos(job.getIntentos() == null ? 1 : job.getIntentos() + 1);
        job.setUltimoError(null);
        repo.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarEnviada(NotificacionJob job, String pdfUrl) {
        job.setEstado(NotificacionJob.ESTADO_ENVIADA);
        job.setPdfUrl(pdfUrl);
        job.setEnviadoEn(Instant.now());
        job.setUltimoError(null);
        repo.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarError(NotificacionJob job, String error) {
        job.setEstado(NotificacionJob.ESTADO_ERROR);
        job.setUltimoError(error != null && error.length() > 500
                ? error.substring(0, 500)
                : error);
        repo.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardarPdfUrl(NotificacionJob job, String pdfUrl) {
        job.setPdfUrl(pdfUrl);
        repo.save(job);
    }
}
