package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.service.NotificacionJobService;
import mx.ferreteria.api.notif.service.NotificacionService;
import mx.ferreteria.api.rh.service.NominaPagadaEvent;
import mx.ferreteria.api.ven.service.VentaCreadaEvent;

/**
 * Único hook AFTER_COMMIT: convierte eventos de dominio (ventas, nómina) en
 * jobs de notificación y los procesa fuera de la transacción de negocio
 * (genera PDF → sube → publica). Si algo falla, la venta/nómina NO se
 * revierte: el job queda en ERROR y el reconciler reintenta.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionCreacionListener {

    private final NotificacionJobService jobService;
    private final NotificacionService notificacionService;

    // REQUIRES_NEW: el callback AFTER_COMMIT corre sobre la sincronización
    // ya commiteada del checkout; sin tx propia, crear+procesar correrían
    // sin transacción JDBC (persist diferido = job perdido en silencio).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVentaCreada(VentaCreadaEvent event) {
        try {
            NotificacionJob job = jobService.crearVentaTicket(event.ventaId());
            notificacionService.procesar(job.getJobId());
        } catch (Exception e) {
            log.warn("notificacion venta post-commit falló venta_id={} err={} (reconciler reintenta)",
                    event.ventaId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNominaPagada(NominaPagadaEvent event) {
        try {
            NotificacionJob job = jobService.crearNominaPagada(event.nominaId());
            notificacionService.procesar(job.getJobId());
        } catch (Exception e) {
            log.warn("notificacion nomina post-commit falló nomina_id={} err={} (reconciler reintenta)",
                    event.nominaId(), e.getMessage());
        }
    }
}
