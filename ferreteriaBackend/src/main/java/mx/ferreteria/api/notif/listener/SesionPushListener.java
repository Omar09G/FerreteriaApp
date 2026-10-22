package mx.ferreteria.api.notif.listener;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.RealtimePushService;
import mx.ferreteria.api.notif.service.RealtimePushService.RealtimeEvento;
import mx.ferreteria.api.seg.service.SesionDesplazadaEvent;

/**
 * Hook de cierre push: ante un takeover single-session empuja
 * SESION_DESPLAZADA a las pestañas conectadas del usuario, excluyendo la
 * sesión recién creada (el equipo que acaba de entrar no se auto-expulsa).
 * Corre AFTER_COMMIT: el front nunca cierra por una sesión que hizo rollback.
 * Sin push (sin stream abierto o sin réplica conectada) el cierre perezoso
 * sigue vigente: el refresh revocado expulsa en el siguiente refresh.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SesionPushListener {

    /** Tipo del evento SSE que el front usa para cerrar la sesión al instante. */
    public static final String TIPO_SESION_DESPLAZADA = "SESION_DESPLAZADA";

    private final RealtimePushService push;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSesionDesplazada(SesionDesplazadaEvent event) {
        try {
            String detalle = "Iniciaste sesión en otro equipo"
                    + (event.ubicacionNueva() == null || event.ubicacionNueva().isBlank()
                            ? "." : " desde " + event.ubicacionNueva() + ".")
                    + " Esta sesión se cerró.";
            push.emitirExcepto(List.of(event.usuarioId()), event.nuevaSesionId(),
                    new RealtimeEvento(Instant.now().toEpochMilli(), TIPO_SESION_DESPLAZADA,
                            "Sesión cerrada en este equipo", detalle, "SESION",
                            (long) event.nuevaSesionId(), Instant.now()));
        } catch (Exception e) {
            log.warn("push sesion-desplazada falló usuario_id={} err={}",
                    event.usuarioId(), e.getMessage());
        }
    }
}
