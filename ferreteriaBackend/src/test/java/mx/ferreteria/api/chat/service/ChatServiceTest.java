package mx.ferreteria.api.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.chat.entity.ChatConversacion;
import mx.ferreteria.api.chat.entity.ChatMensaje;
import mx.ferreteria.api.chat.entity.ChatParticipante;
import mx.ferreteria.api.chat.repo.ChatConversacionRepository;
import mx.ferreteria.api.chat.repo.ChatDirectorioRepository;
import mx.ferreteria.api.chat.repo.ChatMensajeRepository;
import mx.ferreteria.api.chat.repo.ChatParticipanteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.notif.service.BandejaService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    ChatConversacionRepository conversacionRepo;
    @Mock
    ChatParticipanteRepository participanteRepo;
    @Mock
    ChatMensajeRepository mensajeRepo;
    @Mock
    ChatDirectorioRepository directorio;
    @Mock
    BandejaService bandeja;

    @InjectMocks
    ChatService service;

    private ChatParticipante parte(long conv, int user) {
        return ChatParticipante.builder().participanteId(1L)
                .conversacionId(conv).usuarioId(user).build();
    }

    @Test
    @DisplayName("directa existente: la devuelve sin crear")
    void directaExistente_idempotente() {
        when(directorio.existeActivo(2)).thenReturn(true);
        when(participanteRepo.directaEntre(1, 2)).thenReturn(List.of(9L));
        ChatConversacion c = ChatConversacion.builder().conversacionId(9L)
                .tipo("DIRECTA").creadaPor(1).build();
        when(conversacionRepo.findById(9L)).thenReturn(Optional.of(c));
        when(participanteRepo.findByConversacionId(9L))
                .thenReturn(List.of(parte(9L, 1), parte(9L, 2)));
        when(participanteRepo.findByConversacionIdAndUsuarioId(9L, 1))
                .thenReturn(Optional.of(parte(9L, 1)));
        when(directorio.nombresDe(List.of(1, 2))).thenReturn(Map.of(1, "gerente", 2, "cajero"));
        when(mensajeRepo.findFirstByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(9L))
                .thenReturn(null);

        var r = service.crearDirecta(1, 2);

        assertThat(r.conversacionId()).isEqualTo(9L);
        assertThat(r.titulo()).isEqualTo("cajero");
        verify(conversacionRepo, never()).save(any());
    }

    @Test
    @DisplayName("directa consigo mismo: 400")
    void directaConsigoMismo_rechazada() {
        assertThatThrownBy(() -> service.crearDirecta(1, 1))
                .isInstanceOf(mx.ferreteria.api.common.error.ReglaNegocioException.class);
    }

    @Test
    @DisplayName("directa con usuario inactivo: 404")
    void directaInactiva_404() {
        when(directorio.existeActivo(99)).thenReturn(false);

        assertThatThrownBy(() -> service.crearDirecta(1, 99))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("enviar: guarda, avisa a los demás y marca leído propio")
    void enviar_avisaAOtros() {
        ChatParticipante propio = parte(9L, 1);
        when(participanteRepo.findByConversacionIdAndUsuarioId(9L, 1))
                .thenReturn(Optional.of(propio));
        when(mensajeRepo.save(any())).thenAnswer(inv -> {
            ChatMensaje m = inv.getArgument(0);
            m.setMensajeId(77L);
            m.setCreadaEn(Instant.now());
            return m;
        });
        when(participanteRepo.findByConversacionId(9L))
                .thenReturn(List.of(parte(9L, 1), parte(9L, 2)));
        when(directorio.nombresDe(any())).thenReturn(Map.of(1, "gerente", 2, "cajero"));

        var r = service.enviar(1, "gerente", 9L, "  Hola  ");

        assertThat(r.cuerpo()).isEqualTo("Hola");
        verify(bandeja).publicar(eq("CHAT_MENSAJE"), eq("CHAT"), eq(77L),
                anyString(), anyString(), eq(List.of(2)));
        verify(participanteRepo).save(propio);
    }

    @Test
    @DisplayName("historial ajeno: 404 sin filtrar existencia")
    void historialAjeno_404() {
        when(participanteRepo.findByConversacionIdAndUsuarioId(9L, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.historial(1, 9L,
                org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(mensajeRepo, never()).findByConversacionIdAndEliminadaEnIsNullOrderByCreadaEnDesc(
                anyLong(), any());
    }

    @Test
    @DisplayName("grupo sin miembros: 400")
    void grupoSinMiembros_rechazado() {
        assertThatThrownBy(() -> service.crearGrupo(1, "T", List.of()))
                .isInstanceOf(mx.ferreteria.api.common.error.ReglaNegocioException.class);
        verify(bandeja, never()).publicar(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyCollection());
    }
}
