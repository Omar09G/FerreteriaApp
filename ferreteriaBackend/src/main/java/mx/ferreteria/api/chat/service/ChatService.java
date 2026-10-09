package mx.ferreteria.api.chat.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.chat.dto.ChatDtos.ConversacionResponse;
import mx.ferreteria.api.chat.dto.ChatDtos.MensajeResponse;
import mx.ferreteria.api.chat.dto.ChatDtos.ParticipanteResponse;
import mx.ferreteria.api.chat.dto.ChatDtos.UltimoMensajeResponse;
import mx.ferreteria.api.chat.entity.ChatConversacion;
import mx.ferreteria.api.chat.entity.ChatMensaje;
import mx.ferreteria.api.chat.entity.ChatParticipante;
import mx.ferreteria.api.chat.repo.ChatConversacionRepository;
import mx.ferreteria.api.chat.repo.ChatDirectorioRepository;
import mx.ferreteria.api.chat.repo.ChatMensajeRepository;
import mx.ferreteria.api.chat.repo.ChatParticipanteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.service.BandejaService;

/**
 * Chat interno 1 a 1 y por grupos. Toda lectura exige pertenencia (sin
 * IDOR: ajeno a la conversación ve 404, igual que inexistente). Cada mensaje
 * además deja aviso CHAT_MENSAJE en la bandeja de los demás participantes
 * (push SSE por el pipeline existente).
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversacionRepository conversacionRepo;
    private final ChatParticipanteRepository participanteRepo;
    private final ChatMensajeRepository mensajeRepo;
    private final ChatDirectorioRepository directorio;
    private final BandejaService bandeja;

    @Transactional(readOnly = true)
    public List<ConversacionResponse> listar(int usuarioId) {
        List<ChatParticipante> mias = participanteRepo.findByUsuarioId(usuarioId);
        if (mias.isEmpty()) {
            return List.of();
        }
        List<Long> convIds = mias.stream().map(ChatParticipante::getConversacionId).toList();
        Map<Long, ChatConversacion> convs = conversacionRepo.findByConversacionIdIn(convIds)
                .stream().collect(java.util.stream.Collectors
                        .toMap(ChatConversacion::getConversacionId, c -> c, (a, b) -> a));
        Set<Integer> userIds = new LinkedHashSet<>();
        Map<Long, List<ChatParticipante>> parts = new java.util.HashMap<>();
        for (Long id : convIds) {
            List<ChatParticipante> ps = participanteRepo.findByConversacionId(id);
            parts.put(id, ps);
            ps.forEach(p -> userIds.add(p.getUsuarioId()));
        }
        Map<Integer, String> nombres = directorio.nombresDe(new ArrayList<>(userIds));
        Map<Long, ChatParticipante> propios = mias.stream().collect(
                java.util.stream.Collectors.toMap(ChatParticipante::getConversacionId,
                        p -> p, (a, b) -> a));
        List<ConversacionResponse> out = new ArrayList<>();
        for (Long id : convIds) {
            ChatConversacion c = convs.get(id);
            if (c == null) {
                continue;
            }
            List<ParticipanteResponse> ps = parts.getOrDefault(id, List.of()).stream()
                    .map(p -> new ParticipanteResponse(p.getUsuarioId(),
                            nombres.getOrDefault(p.getUsuarioId(), "usuario")))
                    .toList();
            ChatMensaje ultimo = mensajeRepo
                    .findFirstByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(id);
            ChatParticipante propio = propios.get(id);
            out.add(new ConversacionResponse(id, c.getTipo(),
                    tituloPara(c, ps, usuarioId),
                    ps,
                    ultimo == null ? null
                            : new UltimoMensajeResponse(truncar(ultimo.getCuerpo(), 80),
                                    nombres.getOrDefault(ultimo.getAutorId(), "usuario"),
                                    ultimo.getCreadaEn()),
                    noLeidos(id, usuarioId,
                            propio == null ? null : propio.getUltimoLeidoEn())));
        }
        return out;
    }

    /** Directa idempotente: si ya existe entre ambos, la devuelve. */
    @Transactional
    public ConversacionResponse crearDirecta(int usuarioId, Integer otroId) {
        if (otroId == null || otroId == usuarioId) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        exigirActivo(otroId);
        List<Long> existentes = participanteRepo.directaEntre(usuarioId, otroId);
        if (!existentes.isEmpty()) {
            return conversacion(usuarioId, existentes.get(0));
        }
        ChatConversacion c = conversacionRepo.save(ChatConversacion.builder()
                .tipo(ChatConversacion.TIPO_DIRECTA)
                .creadaPor(usuarioId)
                .build());
        agregarParticipante(c.getConversacionId(), usuarioId);
        agregarParticipante(c.getConversacionId(), otroId);
        return conversacion(usuarioId, c.getConversacionId());
    }

    @Transactional
    public ConversacionResponse crearGrupo(int usuarioId, String titulo, List<Integer> miembroIds) {
        Set<Integer> miembros = new LinkedHashSet<>(miembroIds);
        miembros.add(usuarioId);
        if (miembros.size() < 2) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        for (Integer id : miembros) {
            exigirActivo(id);
        }
        ChatConversacion c = conversacionRepo.save(ChatConversacion.builder()
                .tipo(ChatConversacion.TIPO_GRUPO)
                .titulo(titulo.trim())
                .creadaPor(usuarioId)
                .build());
        for (Integer id : miembros) {
            agregarParticipante(c.getConversacionId(), id);
        }
        return conversacion(usuarioId, c.getConversacionId());
    }

    @Transactional(readOnly = true)
    public Page<MensajeResponse> historial(int usuarioId, long conversacionId, Pageable pageable) {
        exigirMiembro(usuarioId, conversacionId);
        Map<Integer, String> nombres = nombresDeConversacion(conversacionId);
        return mensajeRepo
                .findByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(conversacionId, pageable)
                .map(m -> new MensajeResponse(m.getMensajeId(), m.getAutorId(),
                        nombres.getOrDefault(m.getAutorId(), "usuario"),
                        m.getCuerpo(), m.getCreadaEn()));
    }

    @Transactional
    public MensajeResponse enviar(int usuarioId, String username, long conversacionId, String cuerpo) {
        ChatParticipante propio = exigirMiembro(usuarioId, conversacionId);
        ChatMensaje m = mensajeRepo.save(ChatMensaje.builder()
                .conversacionId(conversacionId)
                .autorId(usuarioId)
                .cuerpo(cuerpo.trim())
                .build());
        propio.setUltimoLeidoEn(Instant.now());
        participanteRepo.save(propio);
        Map<Integer, String> nombres = nombresDeConversacion(conversacionId);
        List<Integer> otros = participanteRepo.findByConversacionId(conversacionId).stream()
                .map(ChatParticipante::getUsuarioId)
                .filter(id -> !id.equals(usuarioId))
                .toList();
        bandeja.publicar(NotificacionBandeja.TIPO_CHAT_MENSAJE,
                NotificacionBandeja.REF_CHAT, m.getMensajeId(),
                "Nuevo mensaje de " + username,
                truncar(cuerpo.trim(), 120),
                otros);
        return new MensajeResponse(m.getMensajeId(), usuarioId,
                nombres.getOrDefault(usuarioId, username), m.getCuerpo(), m.getCreadaEn());
    }

    @Transactional
    public void marcarLeida(int usuarioId, long conversacionId) {
        ChatParticipante propio = exigirMiembro(usuarioId, conversacionId);
        propio.setUltimoLeidoEn(Instant.now());
        participanteRepo.save(propio);
    }

    /**
     * Salir de la conversación: quita al participante. Si no queda nadie,
     * borra mensajes y conversación (nadie puede verla ya).
     */
    @Transactional
    public void salir(int usuarioId, long conversacionId) {
        ChatParticipante propio = exigirMiembro(usuarioId, conversacionId);
        participanteRepo.delete(propio);
        if (participanteRepo.findByConversacionId(conversacionId).isEmpty()) {
            mensajeRepo.deleteByConversacionId(conversacionId);
            conversacionRepo.deleteById(conversacionId);
        }
    }

    private ConversacionResponse conversacion(int usuarioId, long conversacionId) {
        ChatConversacion c = conversacionRepo.findById(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        List<ChatParticipante> ps = participanteRepo.findByConversacionId(conversacionId);
        Map<Integer, String> nombres = nombresDe(ps.stream()
                .map(ChatParticipante::getUsuarioId).toList());
        List<ParticipanteResponse> parts = ps.stream()
                .map(p -> new ParticipanteResponse(p.getUsuarioId(),
                        nombres.getOrDefault(p.getUsuarioId(), "usuario")))
                .toList();
        ChatMensaje ultimo = mensajeRepo
                .findFirstByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(conversacionId);
        ChatParticipante propio = exigirMiembro(usuarioId, conversacionId);
        return new ConversacionResponse(conversacionId, c.getTipo(),
                tituloPara(c, parts, usuarioId), parts,
                ultimo == null ? null
                        : new UltimoMensajeResponse(truncar(ultimo.getCuerpo(), 80),
                                nombres.getOrDefault(ultimo.getAutorId(), "usuario"),
                                ultimo.getCreadaEn()),
                noLeidos(conversacionId, usuarioId, propio.getUltimoLeidoEn()));
    }

    /**
     * Sin marca de lectura se cuenta todo (nunca se pasa null a JPQL:
     * PostgreSQL no infiere el tipo del parámetro y falla con 42P18).
     */
    private long noLeidos(long conversacionId, int usuarioId, Instant leido) {
        return leido == null
                ? mensajeRepo.contarNoLeidosTodos(conversacionId, usuarioId)
                : mensajeRepo.contarNoLeidosDesde(conversacionId, usuarioId, leido);
    }

    private ChatParticipante exigirMiembro(int usuarioId, long conversacionId) {
        return participanteRepo.findByConversacionIdAndUsuarioId(conversacionId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private void exigirActivo(Integer usuarioId) {
        if (!directorio.existeActivo(usuarioId)) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
    }

    private void agregarParticipante(long conversacionId, int usuarioId) {
        participanteRepo.save(ChatParticipante.builder()
                .conversacionId(conversacionId)
                .usuarioId(usuarioId)
                .ultimoLeidoEn(Instant.now())
                .build());
    }

    private Map<Integer, String> nombresDeConversacion(long conversacionId) {
        return nombresDe(participanteRepo.findByConversacionId(conversacionId).stream()
                .map(ChatParticipante::getUsuarioId).toList());
    }

    private Map<Integer, String> nombresDe(List<Integer> ids) {
        return directorio.nombresDe(ids);
    }

    private static String tituloPara(ChatConversacion c,
            List<ParticipanteResponse> partes, int usuarioId) {
        if (ChatConversacion.TIPO_GRUPO.equals(c.getTipo())) {
            return c.getTitulo() == null ? "Grupo" : c.getTitulo();
        }
        return partes.stream()
                .filter(p -> !p.usuarioId().equals(usuarioId))
                .map(ParticipanteResponse::username)
                .findFirst().orElse("Conversación");
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
