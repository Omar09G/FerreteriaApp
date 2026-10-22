package mx.ferreteria.api.notif.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * Registro de conexiones SSE por usuario + emisión de eventos en tiempo real.
 * Sin estado persistente: si el pod reinicia, cada front reconecta solo
 * (EventSource) y recupera el historial desde la bandeja. Con más de una
 * réplica, el push solo llega a los conectados a ESTA réplica (documentado en
 * el README; fase 2 = fanout por RabbitMQ).
 */
@Service
@Slf4j
public class RealtimePushService {

    /** Timeout del stream: el front reconecta y reanuda con Last-Event-ID. */
    static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<Suscripcion>> emisores =
            new ConcurrentHashMap<>();

    /**
     * Emisor SSE ligado a la sesión que lo abrió. La sesión se extrae del
     * claim {@code ses} del refresh en el endpoint del stream: sirve para
     * excluir a la sesión nueva cuando se empuja un desplazamiento (si no,
     * el equipo que acaba de entrar se auto-expulsaría). Null = se resolvió
     * sin cookie (clientes no-browser): recibe todo, como antes.
     */
    record Suscripcion(SseEmitter emitter, Integer sesionId) {
    }

    /** Evento que viaja por el stream (JSON). */
    public record RealtimeEvento(
            Long id,
            String tipo,
            String titulo,
            String detalle,
            String refTipo,
            Long refId,
            Instant creadaEn) {
    }

    public SseEmitter suscribir(int usuarioId, Integer sesionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emisores.computeIfAbsent(usuarioId, k -> new CopyOnWriteArrayList<>())
                .add(new Suscripcion(emitter, sesionId));
        Runnable remover = () -> remover(usuarioId, emitter);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover(usuarioId, emitter));
        // Evento inicial: confirma la suscripción sin escribir en bandeja.
        try {
            emitter.send(SseEmitter.event()
                    .name("conectado")
                    .data("ok", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            remover(usuarioId, emitter);
        }
        return emitter;
    }

    private void remover(int usuarioId, SseEmitter emitter) {
        List<Suscripcion> lista = emisores.get(usuarioId);
        if (lista != null) {
            lista.removeIf(s -> s.emitter() == emitter);
            if (lista.isEmpty()) {
                emisores.remove(usuarioId, lista);
            }
        }
    }

    /**
     * Emite a los conectados. Si hay transacción activa, el envío se difiere
     * a AFTER_COMMIT: el front nunca recibe un aviso de algo que hizo
     * rollback. Sin transacción, se envía de inmediato.
     */
    public void emitir(Collection<Integer> destinatarios, RealtimeEvento evento) {
        emitirExcepto(destinatarios, null, evento);
    }

    /**
     * Emite excluyendo los emisores de una sesión (takeover single-session:
     * el equipo que acaba de entrar no debe recibir su propio desplazamiento
     * y auto-expulsarse; las pestañas de las sesiones anteriores sí).
     *
     * @param excluirSesionId sesión a saltar, o null para emitir a todos.
     */
    public void emitirExcepto(Collection<Integer> destinatarios, Integer excluirSesionId,
            RealtimeEvento evento) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarAhora(destinatarios, excluirSesionId, evento);
                }
            });
        } else {
            enviarAhora(destinatarios, excluirSesionId, evento);
        }
    }

    private void enviarAhora(Collection<Integer> destinatarios, Integer excluirSesionId,
            RealtimeEvento evento) {
        for (Integer usuarioId : destinatarios) {
            List<Suscripcion> lista = emisores.get(usuarioId);
            if (lista == null || lista.isEmpty()) {
                continue;
            }
            for (Suscripcion s : lista) {
                if (excluirSesionId != null && excluirSesionId.equals(s.sesionId())) {
                    continue;
                }
                try {
                    enviarA(s, evento);
                } catch (Exception e) {
                    podar(usuarioId, s.emitter(), e);
                }
            }
        }
    }

    /** Envía a un emisor (paso separado para poder observarlo en tests). */
    void enviarA(Suscripcion s, RealtimeEvento evento) throws Exception {
        s.emitter().send(SseEmitter.event()
                .id(String.valueOf(evento.id()))
                .name("notificacion")
                .data(evento, MediaType.APPLICATION_JSON));
    }

    /** Cierra y quita un emisor muerto sin romper el envío a los demás. */
    private void podar(int usuarioId, SseEmitter emitter, Exception causa) {
        try {
            emitter.completeWithError(causa);
        } catch (Exception ignored) {
            // Sin request vivo ya no hay nada que completar.
        }
        remover(usuarioId, emitter);
    }

    /** Latido cada 30 s: mantiene vivo el stream y poda conexiones muertas. */
    @Scheduled(fixedDelay = 30_000)
    public void latido() {
        emisores.forEach((usuarioId, lista) -> {
            for (Suscripcion s : lista) {
                try {
                    s.emitter().send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    podar(usuarioId, s.emitter(), e);
                }
            }
        });
    }

    int conexionesActivas() {
        return emisores.values().stream().mapToInt(List::size).sum();
    }
}
