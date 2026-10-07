package mx.ferreteria.api.notif.listener;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.notif.service.NotificacionService;

/**
 * Reconciliador periódico: reintenta jobs PENDIENTE/ERROR con intentos
 * bajo el máximo (broker caído, fallo transitorio de storage/PDF).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionReconciler {

    private final NotificacionJobRepository jobRepo;
    private final NotificacionService notificacionService;
    private final NotificacionProperties props;

    @Scheduled(fixedDelayString = "${app.notif.reconcile-delay-ms:30000}")
    public void reconciliar() {
        List<NotificacionJob> pendientes = jobRepo.pendientesParaReconciliar(
                props.maxIntentos(), java.time.Instant.now().minusSeconds(600));
        for (NotificacionJob job : pendientes) {
            try {
                notificacionService.procesar(job.getJobId());
            } catch (Exception e) {
                log.warn("reconcile fallo job_id={} err={}", job.getJobId(), e.getMessage());
            }
        }
    }
}
