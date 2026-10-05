package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import mx.ferreteria.api.notif.config.NotificacionProperties;

@ExtendWith(MockitoExtension.class)
class OtpWhatsappAdapterTest {

    @Mock
    ObjectProvider<WhatsAppNotificacionSender> provider;
    @Mock
    WhatsAppNotificacionSender sender;

    private static NotificacionProperties props() {
        return new NotificacionProperties(false, null, 0, null,
                new NotificacionProperties.WhatsApp(false, "mock", null,
                        "ferreteria", null, "521"));
    }

    @Test
    @DisplayName("con sender disponible: delega el texto")
    void conSender_delega() {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.sendTexto(eq("5551234567"), anyString())).thenReturn(true);
        var adapter = new OtpWhatsappAdapter(props(), new WhatsAppMockBandeja(), provider);

        assertThat(adapter.enviarTexto("5551234567", "codigo 482913")).isTrue();
        verify(sender).sendTexto(eq("5551234567"), anyString());
    }

    @Test
    @DisplayName("sin sender: captura en la bandeja mock con prefijo")
    void sinSender_mock() {
        when(provider.getIfAvailable()).thenReturn(null);
        var bandeja = new WhatsAppMockBandeja();
        var adapter = new OtpWhatsappAdapter(props(), bandeja, provider);

        assertThat(adapter.enviarTexto("5551234567", "codigo 482913")).isTrue();
        assertThat(bandeja.mensajes()).hasSize(1);
        assertThat(bandeja.mensajes().get(0).numero()).isEqualTo("5215551234567");
    }
}
