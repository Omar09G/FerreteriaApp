package mx.ferreteria.api.notif.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;
import mx.ferreteria.api.notif.entity.NotificacionJob;

/**
 * Adaptador de mensajería: publica el payload del job en el exchange topic.
 * Devuelve si el broker aceptó el mensaje; si no, el servicio marca ERROR y
 * el reconciler reintenta (venta/nómina ya commiteada, nunca se revierte).
 * Es @Component (como los senders de canal), no servicio de dominio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class NotificacionPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final NotificacionProperties props;

    public boolean publicar(NotificacionJob job, NotificacionMensaje mensaje) {
        try {
            rabbitTemplate.convertAndSend(
                    props.rabbit().exchange(),
                    props.rabbit().routingKey(),
                    mensaje);
            log.info("notificacion publicada job_id={} tipo={} ref={}/{}",
                    job.getJobId(), job.getTipo(), job.getRefTipo(), job.getRefId());
            return true;
        } catch (Exception e) {
            log.warn("broker no disponible, job queda para reconciler job_id={} err={}",
                    job.getJobId(), e.getMessage());
            return false;
        }
    }
}
