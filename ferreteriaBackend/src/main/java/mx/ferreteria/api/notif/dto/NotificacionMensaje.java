package mx.ferreteria.api.notif.dto;

import java.math.BigDecimal;

/**
 * Payload JSON publicado en RabbitMQ cuando hay un trabajo de notificación
 * listo. Sin binarios: el consumer descarga el PDF por clave.
 */
public record NotificacionMensaje(
        Long jobId,
        String tipo,
        String refTipo,
        Long refId,
        String pdfUrl,
        String paraEmail,
        String paraWhatsapp,
        String asunto,
        BigDecimal total) {
}
