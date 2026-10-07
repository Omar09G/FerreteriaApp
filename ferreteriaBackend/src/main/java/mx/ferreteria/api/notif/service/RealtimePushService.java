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

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> emisores =
            new ConcurrentHashMap<>();

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

    public SseEmitter suscribir(int usuarioId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emisores.computeIfAbsent(usuarioId, k -> new CopyOnWriteArrayList<>()).add(emitter);
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
        List<SseEmitter> lista = emisores.get(usuarioId);
        if (lista != null) {
            lista.remove(emitter);
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
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarAhora(destinatarios, evento);
                }
            });
        } else {
            enviarAhora(destinatarios, evento);
        }
    }

    private void enviarAhora(Collection<Integer> destinatarios, RealtimeEvento evento) {
        for (Integer usuarioId : destinatarios) {
            List<SseEmitter> lista = emisores.get(usuarioId);
            if (lista == null || lista.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : lista) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(evento.id()))
                            .name("notificacion")
                            .data(evento, MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    remover(usuarioId, emitter);
                }
            }
        }
    }

    /** Latido cada 30 s: mantiene vivo el stream y poda conexiones muertas. */
    @Scheduled(fixedDelay = 30_000)
    public void latido() {
        emisores.forEach((usuarioId, lista) -> {
            for (SseEmitter emitter : lista) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    remover(usuarioId, emitter);
                }
            }
        });
    }

    int conexionesActivas() {
        return emisores.values().stream().mapToInt(List::size).sum();
    }
}
