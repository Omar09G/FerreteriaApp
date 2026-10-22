package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.notif.service.RealtimePushService.RealtimeEvento;
import mx.ferreteria.api.notif.service.RealtimePushService.Suscripcion;

class RealtimePushServiceTest {

    /** Graba a qué sesiones se intentó enviar (sin red ni requests). */
    static class Grabadora extends RealtimePushService {
        final List<Integer> enviadasA = new ArrayList<>();

        @Override
        void enviarA(Suscripcion s, RealtimeEvento evento) {
            enviadasA.add(s.sesionId());
        }
    }

    private static RealtimeEvento evento() {
        return new RealtimeEvento(1L, "X", "t", "d", "SESION", 9L, Instant.now());
    }

    @Test
    @DisplayName("emitirExcepto salta solo la sesión excluida")
    void emitirExcepto_excluyeSesionNueva() {
        var svc = new Grabadora();
        svc.suscribir(7, 41);
        svc.suscribir(7, 42);
        svc.suscribir(7, null);

        svc.emitirExcepto(List.of(7), 42, evento());

        assertThat(svc.enviadasA).containsExactlyInAnyOrder(41, null);
    }

    @Test
    @DisplayName("emitirExcepto con null emite a todos (comportamiento previo)")
    void emitirExcepto_nullEmiteATodos() {
        var svc = new Grabadora();
        svc.suscribir(7, 41);
        svc.suscribir(7, 42);

        svc.emitirExcepto(List.of(7), null, evento());

        assertThat(svc.enviadasA).containsExactlyInAnyOrder(41, 42);
    }

    @Test
    @DisplayName("suscribir registra la conexión por usuario")
    void suscribir_registra() {
        var svc = new Grabadora();
        assertThat(svc.conexionesActivas()).isZero();

        svc.suscribir(7, 41);
        svc.suscribir(7, 42);
        svc.suscribir(9, 43);

        assertThat(svc.conexionesActivas()).isEqualTo(3);
    }
}
