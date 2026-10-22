package mx.ferreteria.api.notif.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.notif.service.RealtimePushService;
import mx.ferreteria.api.notif.service.RealtimePushService.RealtimeEvento;
import mx.ferreteria.api.seg.service.SesionDesplazadaEvent;

@ExtendWith(MockitoExtension.class)
class SesionPushListenerTest {

    @Mock
    RealtimePushService push;

    @Test
    @DisplayName("takeover: empuja SESION_DESPLAZADA excluyendo la sesión nueva")
    void onSesionDesplazada_empujaExcluyendoNueva() {
        var listener = new SesionPushListener(push);

        listener.onSesionDesplazada(
                new SesionDesplazadaEvent(7, 42, "Guadalajara, Jalisco, México"));

        var evento = ArgumentCaptor.forClass(RealtimeEvento.class);
        verify(push).emitirExcepto(eq(List.of(7)), eq(42), evento.capture());
        assertThat(evento.getValue().tipo())
                .isEqualTo(SesionPushListener.TIPO_SESION_DESPLAZADA);
        assertThat(evento.getValue().detalle()).contains("Guadalajara, Jalisco, México");
        assertThat(evento.getValue().refTipo()).isEqualTo("SESION");
        assertThat(evento.getValue().refId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("sin ubicación: el detalle no menciona ciudad")
    void onSesionDesplazada_sinUbicacion() {
        var listener = new SesionPushListener(push);

        listener.onSesionDesplazada(new SesionDesplazadaEvent(7, 42, null));

        var evento = ArgumentCaptor.forClass(RealtimeEvento.class);
        verify(push).emitirExcepto(eq(List.of(7)), eq(42), evento.capture());
        assertThat(evento.getValue().detalle()).doesNotContain("desde");
        assertThat(evento.getValue().detalle()).contains("se cerró");
    }

    @Test
    @DisplayName("push caído: no propaga la excepción (el login ya commiteó)")
    void onSesionDesplazada_pushFalla_noLanza() {
        var listener = new SesionPushListener(push);
        org.mockito.Mockito.doThrow(new RuntimeException("broker"))
                .when(push).emitirExcepto(any(), any(), any());

        listener.onSesionDesplazada(new SesionDesplazadaEvent(7, 42, null));

        verify(push).emitirExcepto(eq(List.of(7)), eq(42), any());
    }
}
