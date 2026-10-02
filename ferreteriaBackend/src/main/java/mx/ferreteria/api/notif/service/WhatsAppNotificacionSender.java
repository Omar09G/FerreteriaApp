package mx.ferreteria.api.notif.service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.config.NotificacionProperties;

/**
 * WhatsApp con contrato Evolution API (sendText/sendMedia) y mock en proceso.
 * <ul>
 * <li>{@code enabled=false} (default): no-op, solo log.</li>
 * <li>{@code proveedor=mock} (default): captura en {@link WhatsAppMockBandeja}
 * (par de Mailpit para email). Cero infraestructura.</li>
 * <li>{@code proveedor=evolution} + {@code base-url/instancia/api-key}:
 * envío real a Evolution API sin cambiar código.</li>
 * </ul>
 * Nunca lanza: un fallo del canal se loguea y se reporta con
 * {@code false} (igual que Telegram). El número se normaliza a solo dígitos; si trae 10 dígitos se
 * antepone {@code prefijo-por-defecto} (521 México).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class WhatsAppNotificacionSender {

    private final NotificacionProperties props;
    private final WhatsAppMockBandeja bandeja;
    private RestClient restClient = RestClient.create();

    /**
     * Fail-fast anti-SSRF al arrancar: el base-url de Evolution API viene de
     * env y se usa para POSTs salientes; solo se admite https público (sin
     * IPs literales, localhost ni metadata cloud 169.254.169.254).
     */
    @PostConstruct
    void validarBaseUrl() {
        NotificacionProperties.WhatsApp cfg = props.whatsapp();
        if (cfg == null || !"evolution".equalsIgnoreCase(cfg.proveedor())) {
            return;
        }
        String base = cfg.baseUrl() == null ? "" : cfg.baseUrl().trim();
        String host = "";
        try {
            var uri = new java.net.URI(base);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("esquema");
            }
            host = String.valueOf(uri.getHost()).toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "WHATSAPP_BASE_URL debe ser https://host valido, valor actual: " + base);
        }
        if (host.isBlank() || host.equals("localhost")
                || host.startsWith("127.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.startsWith("169.254.")
                || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+|\\[.*\\]")) {
            throw new IllegalStateException(
                    "WHATSAPP_BASE_URL no admite host interno o IP literal: " + base);
        }
    }

    public boolean send(String telefono, String asunto, byte[] pdf) {
        NotificacionProperties.WhatsApp cfg = props.whatsapp();
        if (cfg == null || !cfg.enabled()) {
            log.debug("whatsapp omitido (deshabilitado) to={}", telefono);
            return false;
        }
        String numero = normalizar(telefono);
        if (numero == null || numero.isBlank()) {
            log.debug("whatsapp omitido (sin número)");
            return false;
        }
        if ("evolution".equalsIgnoreCase(cfg.proveedor()) && cfg.baseUrl() != null
                && !cfg.baseUrl().isBlank()) {
            return enviarEvolution(cfg, numero, asunto, pdf);
        }
        bandeja.registrar(numero, asunto, "documento.pdf", pdf);
        log.info("whatsapp mock to={} asunto={}", numero, asunto);
        return true;
    }

    private boolean enviarEvolution(NotificacionProperties.WhatsApp cfg, String numero, String asunto,
            byte[] pdf) {
        try {
            String base = cfg.baseUrl().replaceAll("/+$", "");
            String instancia = cfg.instancia() != null && !cfg.instancia().isBlank()
                    ? cfg.instancia()
                    : "ferreteria";
            Map<String, Object> texto = new HashMap<>();
            texto.put("number", numero);
            texto.put("text", asunto != null ? asunto : "Documento");
            restClient.post()
                    .uri(base + "/message/sendText/{instancia}", instancia)
                    .header("apikey", cfg.apiKey() != null ? cfg.apiKey() : "")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(texto)
                    .retrieve()
                    .toBodilessEntity();
            if (pdf != null) {
                Map<String, Object> media = new HashMap<>();
                media.put("number", numero);
                media.put("mediatype", "document");
                media.put("media", Base64.getEncoder().encodeToString(pdf));
                media.put("fileName", "documento.pdf");
                media.put("caption", asunto != null ? asunto : "Documento");
                restClient.post()
                        .uri(base + "/message/sendMedia/{instancia}", instancia)
                        .header("apikey", cfg.apiKey() != null ? cfg.apiKey() : "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(media)
                        .retrieve()
                        .toBodilessEntity();
            }
            log.info("whatsapp evolution enviado to={}", numero);
            return true;
        } catch (Exception e) {
            log.warn("whatsapp evolution fallo to={} err={}", numero, e.getMessage());
            return false;
        }
    }

    static String normalizar(String telefono, String prefijoPorDefecto) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.length() == 10 && prefijoPorDefecto != null && !prefijoPorDefecto.isBlank()) {
            return prefijoPorDefecto + digitos;
        }
        return digitos;
    }

    private String normalizar(String telefono) {
        NotificacionProperties.WhatsApp cfg = props.whatsapp();
        String prefijo = cfg != null ? cfg.prefijoPorDefecto() : null;
        return normalizar(telefono, prefijo);
    }

}
