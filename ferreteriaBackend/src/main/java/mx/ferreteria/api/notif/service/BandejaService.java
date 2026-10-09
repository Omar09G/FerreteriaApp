package mx.ferreteria.api.notif.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.notif.dto.BandejaDtos.BandejaResponse;
import mx.ferreteria.api.notif.dto.BandejaDtos.NoLeidasResponse;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.repo.NotificacionBandejaRepository;
import mx.ferreteria.api.notif.service.RealtimePushService.RealtimeEvento;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;

/**
 * Bandeja de notificaciones en tiempo real: persiste una fila por
 * destinatario y evento (idempotente) y empuja el aviso por SSE a los
 * conectados. El push se difiere a AFTER_COMMIT en
 * {@link RealtimePushService}: un rollback nunca notifica.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BandejaService {

    /** Días de retención del historial (purga diaria del scheduler). */
    static final long RETENCION_DIAS = 90;

    private final NotificacionBandejaRepository repo;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final RealtimePushService push;

    /**
     * Publica a destinatarios explícitos. Idempotente: republicar el mismo
     * (usuario, tipo, ref) no duplica (unique de BD).
     */
    @Transactional
    public void publicar(String tipo, String refTipo, long refId,
            String titulo, String detalle, Collection<Integer> destinatarios) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        Set<Integer> unicos = new LinkedHashSet<>(destinatarios);
        for (Integer usuarioId : unicos) {
            if (usuarioId == null || usuarioId == 0) {
                continue;
            }
            NotificacionBandeja fila = NotificacionBandeja.builder()
                    .usuarioId(usuarioId)
                    .tipo(tipo)
                    .titulo(truncar(titulo, 140))
                    .detalle(detalle)
                    .refTipo(refTipo)
                    .refId(refId)
                    .creadaEn(Instant.now())
                    .build();
            try {
                NotificacionBandeja saved = repo.saveAndFlush(fila);
                // Un evento por fila (cada una lleva su id: el front
                // deduplica por id si reconecta a mitad del stream).
                push.emitir(List.of(usuarioId), aEvento(saved));
            } catch (DataIntegrityViolationException duplicado) {
                // Republicación del mismo evento: ya existe, no duplica.
                log.debug("bandeja duplicada usuario={} tipo={} ref={}/{}",
                        usuarioId, tipo, refTipo, refId);
            }
        }
    }

    /**
     * Publica a GERENTES y ADMINISTRADORES activos (recordatorios y eventos
     * de dominio). Solo GERENCIA: los avisos propios van con
     * {@link #publicar}.
     */
    @Transactional
    public void publicarParaGerencia(String tipo, String refTipo, long refId,
            String titulo, String detalle) {
        publicar(tipo, refTipo, refId, titulo, detalle,
                destinatarioRepo.findGerenteAdminIds());
    }

    @Transactional(readOnly = true)
    public Page<BandejaResponse> listar(int usuarioId, Pageable pageable) {
        return repo.findByUsuarioIdOrderByCreadaEnDesc(usuarioId, pageable).map(this::aResponse);
    }

    @Transactional(readOnly = true)
    public NoLeidasResponse noLeidas(int usuarioId) {
        return new NoLeidasResponse(repo.countByUsuarioIdAndLeidaEnIsNull(usuarioId));
    }

    @Transactional
    public BandejaResponse marcarLeida(int usuarioId, long bandejaId) {
        NotificacionBandeja fila = repo.findByBandejaIdAndUsuarioId(bandejaId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (fila.getLeidaEn() == null) {
            fila.setLeidaEn(Instant.now());
            repo.save(fila);
        }
        return aResponse(fila);
    }

    @Transactional
    public long marcarTodasLeidas(int usuarioId) {
        return repo.marcarTodasLeidas(usuarioId, Instant.now());
    }

    /** Borra el historial ya leído del usuario (las no leídas se conservan). */
    @Transactional
    public long eliminarLeidas(int usuarioId) {
        return repo.eliminarLeidas(usuarioId);
    }

    /** Purga nocturna del historial (retención 90 días). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgarAntiguas() {
        int borradas = repo.purgarAnterioresA(Instant.now().minus(RETENCION_DIAS, ChronoUnit.DAYS));
        if (borradas > 0) {
            log.info("bandeja purgada filas={}", borradas);
        }
        return borradas;
    }

    private RealtimeEvento aEvento(NotificacionBandeja b) {
        return new RealtimeEvento(b.getBandejaId(), b.getTipo(), b.getTitulo(),
                b.getDetalle(), b.getRefTipo(), b.getRefId(), b.getCreadaEn());
    }

    private BandejaResponse aResponse(NotificacionBandeja b) {
        return new BandejaResponse(b.getBandejaId(), b.getTipo(), b.getTitulo(),
                b.getDetalle(), b.getRefTipo(), b.getRefId(), b.getLeidaEn(), b.getCreadaEn());
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
