package mx.ferreteria.api.notif.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de notificaciones: broker, canales y límite de reintentos.
 */
@ConfigurationProperties(prefix = "app.notif")
public record NotificacionProperties(
        boolean enabled,
        Rabbit rabbit,
        int maxIntentos,
        Telegram telegram,
        WhatsApp whatsapp) {

    public record Rabbit(
            String exchange,
            String routingKey,
            String queue,
            String dlq) {
    }

    public record Telegram(String botToken, String chatId) {
    }

    public record WhatsApp(
            boolean enabled,
            String proveedor,
            String baseUrl,
            String instancia,
            String apiKey,
            String prefijoPorDefecto) {
    }
}
