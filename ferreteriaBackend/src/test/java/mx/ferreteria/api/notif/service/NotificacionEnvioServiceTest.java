package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.storage.DocumentoStoragePort;
import mx.ferreteria.api.notif.config.NotificacionProperties;
import mx.ferreteria.api.notif.config.NotificacionProperties.WhatsApp;
import mx.ferreteria.api.notif.dto.NotificacionMensaje;

class NotificacionEnvioServiceTest {

    private static final byte[] PDF = new byte[] { 0x25, 0x50, 0x44, 0x46 };

    @Test
    @DisplayName("enviar con whatsapp mock: descarga el PDF y lo captura al número del cliente")
    void enviar_whatsappMockCaptura() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        when(storage.descargarPdf("tickets/1.pdf")).thenReturn(PDF);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(true, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        WhatsAppMockBandeja bandeja = new WhatsAppMockBandeja();
        WhatsAppNotificacionSender whatsappSender =
                new WhatsAppNotificacionSender(props, bandeja);
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender, whatsappSender);
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                "tickets/1.pdf", null, "5550001111", "Ticket V-1", new BigDecimal("116.00"));

        service.enviar(msg);

        verify(storage).descargarPdf("tickets/1.pdf");
        assertThat(bandeja.mensajes()).hasSize(1);
        assertThat(bandeja.mensajes().get(0).numero()).isEqualTo("5215550001111");
        assertThat(bandeja.mensajes().get(0).pdf()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("enviar sin destinatarios: omite todos los canales sin fallar")
    void enviar_sinDestinatarios_omite() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(false, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        WhatsAppMockBandeja bandeja = new WhatsAppMockBandeja();
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, bandeja));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                null, " ", " ", "Ticket V-1", BigDecimal.ONE);

        service.enviar(msg);

        verify(emailSender, org.mockito.Mockito.never()).send(any(), any(), any(), any());
        assertThat(bandeja.mensajes()).isEmpty();
    }

    @Test
    @DisplayName("email falla + whatsapp ok: whatsapp se intenta igual y no lanza (ENVIADA parcial)")
    void enviar_emailFalla_whatsappSeIntenta() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        when(storage.descargarPdf("tickets/1.pdf")).thenReturn(PDF);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(true, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp caído"))
                .when(emailSender).send(any(), any(), any(), any());
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        WhatsAppMockBandeja bandeja = new WhatsAppMockBandeja();
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, bandeja));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                "tickets/1.pdf", "cte@acme.mx", "5550001111", "Ticket V-1", BigDecimal.ONE);

        service.enviar(msg);

        assertThat(bandeja.mensajes()).hasSize(1);
        assertThat(bandeja.mensajes().get(0).numero()).isEqualTo("5215550001111");
    }

    @Test
    @DisplayName("todos los canales fallan: lanza para que el job vaya a ERROR y reintente")
    void enviar_todoFalla_lanza() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        when(storage.descargarPdf("tickets/1.pdf")).thenReturn(PDF);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(false, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp caído"))
                .when(emailSender).send(any(), any(), any(), any());
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, new WhatsAppMockBandeja()));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                "tickets/1.pdf", "cte@acme.mx", null, "Ticket V-1", BigDecimal.ONE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enviar(msg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("smtp caído");
    }

    @Test
    @DisplayName("whatsapp deshabilitado + email falla: lanza (el no-op no enmascara el fallo)")
    void enviar_whatsappDeshabilitado_emailFalla_lanza() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        when(storage.descargarPdf("tickets/1.pdf")).thenReturn(PDF);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(false, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        org.mockito.Mockito.doThrow(new RuntimeException("smtp caído"))
                .when(emailSender).send(any(), any(), any(), any());
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        WhatsAppMockBandeja bandeja = new WhatsAppMockBandeja();
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, bandeja));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                "tickets/1.pdf", "cte@acme.mx", "5550001111", "Ticket V-1", BigDecimal.ONE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enviar(msg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("smtp caído");
        assertThat(bandeja.mensajes()).isEmpty();
    }

    @Test
    @DisplayName("solo whatsapp con datos pero deshabilitado: lanza en vez de ENVIADA silenciosa")
    void enviar_soloWhatsappDeshabilitado_lanza() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(false, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, new WhatsAppMockBandeja()));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                null, null, "5550001111", "Ticket V-1", BigDecimal.ONE);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enviar(msg))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("enviar con email: usa el sender de email con el PDF adjunto")
    void enviar_emailAdjuntaPdf() {
        DocumentoStoragePort storage = mock(DocumentoStoragePort.class);
        when(storage.descargarPdf("tickets/1.pdf")).thenReturn(PDF);
        NotificacionProperties props = new NotificacionProperties(false, null, 5, null,
                new WhatsApp(false, "mock", "", "ferreteria", "", "521"));
        EmailNotificacionSender emailSender = mock(EmailNotificacionSender.class);
        TelegramNotificacionSender telegramSender = mock(TelegramNotificacionSender.class);
        NotificacionEnvioService service = new NotificacionEnvioService(
                storage, props, emailSender, telegramSender,
                new WhatsAppNotificacionSender(props, new WhatsAppMockBandeja()));
        NotificacionMensaje msg = new NotificacionMensaje(7L, "VENTA_TICKET", "VENTA", 1L,
                "tickets/1.pdf", "cte@acme.mx", null, "Ticket V-1", BigDecimal.ONE);

        service.enviar(msg);

        verify(emailSender).send(eq("cte@acme.mx"), eq("Ticket V-1"), eq(PDF), eq("tickets/1.pdf"));
    }
}
