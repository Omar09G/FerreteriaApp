package mx.ferreteria.api.notif.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.notif.service.NotificacionEnvioService;
import mx.ferreteria.api.notif.service.NotificacionJobService;

/**
 * Consumer de la cola notificacion.jobs. Descarga el PDF y envía por
 * email / Telegram / WhatsApp (stub). Marca el job ENVIADA o ERROR.
 * No relanza: el reconciler es la única vía de reintento (evita doble
 * envío consumer+reconciler); la DLQ queda solo para mensajes veneno.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionListener {

    private final NotificacionEnvioService envioService;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;

    @RabbitListener(queues = "${app.notif.rabbit.queue:notificacion.jobs}")
    public void onMensaje(NotificacionMensaje msg) {
        log.info("notificacion recibida job_id={} tipo={}", msg.jobId(), msg.tipo());
        try {
            envioService.enviar(msg);
            jobRepo.findById(msg.jobId()).ifPresent(job ->
                    jobService.marcarEnviada(job, msg.pdfUrl()));
        } catch (Exception e) {
            log.warn("envio fallo job_id={} err={} (reconciler reintenta)",
                    msg.jobId(), e.getMessage());
            jobRepo.findById(msg.jobId()).ifPresent(job ->
                    jobService.marcarError(job, e.getMessage()));
        }
    }
}
