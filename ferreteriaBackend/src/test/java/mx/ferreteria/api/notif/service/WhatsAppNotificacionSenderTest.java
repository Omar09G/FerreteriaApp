package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.config.NotificacionProperties.WhatsApp;

class WhatsAppNotificacionSenderTest {

    private static final byte[] PDF = new byte[] { 0x25, 0x50, 0x44, 0x46 };

    WhatsAppMockBandeja bandeja;

    @BeforeEach
    void setUp() {
        bandeja = new WhatsAppMockBandeja();
    }

    private static NotificacionProperties props(boolean enabled, String proveedor) {
        return props(enabled, proveedor, "http://evo:8080");
    }

    private static NotificacionProperties props(boolean enabled, String proveedor, String baseUrl) {
        return new NotificacionProperties(false, null, 5, null,
                new WhatsApp(enabled, proveedor, baseUrl, "ferreteria", "clave123", "521"));
    }

    /** Servidor HTTP del JDK que registra (método, path, apikey, cuerpo). */
    static class ServidorFake implements AutoCloseable {
        record Peticion(String metodo, String path, String apikey, String cuerpo) {
        }

        final HttpServer http;
        final List<Peticion> peticiones = Collections.synchronizedList(new ArrayList<>());
        volatile int estatus = 200;

        ServidorFake() throws IOException {
            http = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            http.createContext("/", ex -> {
                String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                peticiones.add(new Peticion(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                        ex.getRequestHeaders().getFirst("apikey"), cuerpo));
                byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(estatus, resp.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(resp);
                }
            });
            http.start();
        }

        String baseUrl() {
            return "http://localhost:" + http.getAddress().getPort();
        }

        @Override
        public void close() {
            http.stop(0);
        }
    }

    @Test
    @DisplayName("mock: captura número normalizado + asunto + pdf sin HTTP")
    void mock_captura() {
        WhatsAppNotificacionSender sender =
                new WhatsAppNotificacionSender(props(true, "mock"), bandeja);

        boolean entregado = sender.send("555 000-1111", "Ticket V-1", PDF);

        assertThat(entregado).isTrue();

        assertThat(bandeja.mensajes()).hasSize(1);
        WhatsAppMockBandeja.MensajeMock m = bandeja.mensajes().get(0);
        assertThat(m.numero()).isEqualTo("5215550001111");
        assertThat(m.asunto()).isEqualTo("Ticket V-1");
        assertThat(m.pdf()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("deshabilitado: no captura nada")
    void deshabilitado_noCaptura() {
        WhatsAppNotificacionSender sender =
                new WhatsAppNotificacionSender(props(false, "mock"), bandeja);

        boolean entregado = sender.send("5550001111", "Ticket V-1", PDF);

        assertThat(entregado).isFalse();

        assertThat(bandeja.mensajes()).isEmpty();
    }

    @Test
    @DisplayName("sin número: se omite sin fallar")
    void sinNumero_seOmite() {
        WhatsAppNotificacionSender sender =
                new WhatsAppNotificacionSender(props(true, "mock"), bandeja);

        boolean entregado = sender.send("   ", "Ticket V-1", PDF);

        assertThat(entregado).isFalse();

        assertThat(bandeja.mensajes()).isEmpty();
    }

    @Test
    @DisplayName("evolution: POST sendText + sendMedia(documento base64) con apikey")
    void evolution_contrato() throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (ServidorFake server = new ServidorFake()) {
            WhatsAppNotificacionSender sender = new WhatsAppNotificacionSender(
                    props(true, "evolution", server.baseUrl()), bandeja);

            sender.send("(555) 000-1111", "Ticket V-1", PDF);

            assertThat(server.peticiones).hasSize(2);
            ServidorFake.Peticion texto = server.peticiones.get(0);
            assertThat(texto.metodo()).isEqualTo("POST");
            assertThat(texto.path()).isEqualTo("/message/sendText/ferreteria");
            assertThat(texto.apikey()).isEqualTo("clave123");
            assertThat(om.readTree(texto.cuerpo()).get("number").asText()).isEqualTo("5215550001111");
            assertThat(om.readTree(texto.cuerpo()).get("text").asText()).isEqualTo("Ticket V-1");
            ServidorFake.Peticion media = server.peticiones.get(1);
            assertThat(media.path()).isEqualTo("/message/sendMedia/ferreteria");
            assertThat(om.readTree(media.cuerpo()).get("mediatype").asText()).isEqualTo("document");
            assertThat(Base64.getDecoder().decode(om.readTree(media.cuerpo()).get("media").asText()))
                    .isEqualTo(PDF);
            assertThat(om.readTree(media.cuerpo()).get("fileName").asText()).isEqualTo("documento.pdf");
            assertThat(bandeja.mensajes()).isEmpty();
        }
    }

    @Test
    @DisplayName("evolution caído: no lanza, no captura en mock")
    void evolution_caido_noLanza() throws Exception {
        try (ServidorFake server = new ServidorFake()) {
            server.estatus = 500;
            WhatsAppNotificacionSender sender = new WhatsAppNotificacionSender(
                    props(true, "evolution", server.baseUrl()), bandeja);

            java.util.concurrent.atomic.AtomicBoolean entregado = new java.util.concurrent.atomic.AtomicBoolean(true);
            assertThatCode(() -> entregado.set(sender.send("5550001111", "Ticket V-1", PDF)))
                    .doesNotThrowAnyException();

            assertThat(entregado.get()).isFalse();

            assertThat(bandeja.mensajes()).isEmpty();
        }
    }
}
