package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.repo.NotificacionBandejaRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BandejaServiceTest {

    @Mock
    NotificacionBandejaRepository repo;

    @Mock
    InformeDestinatarioRepository destinatarioRepo;

    @Mock
    RealtimePushService push;

    @InjectMocks
    BandejaService service;

    private NotificacionBandeja fila(int usuario, String tipo) {
        return NotificacionBandeja.builder()
                .bandejaId(1L).usuarioId(usuario).tipo(tipo)
                .titulo("T").detalle("D").refTipo("VENTA").refId(5L)
                .creadaEn(Instant.now()).build();
    }

    @Test
    @DisplayName("publicar: una fila por destinatario + push por fila")
    void publicar_unaFilaPorDestinatario() {
        when(repo.saveAndFlush(any()))
                .thenAnswer(inv -> {
                    NotificacionBandeja b = inv.getArgument(0);
                    b.setBandejaId((long) b.getUsuarioId());
                    return b;
                });

        service.publicar("VENTA_TICKET", "VENTA", 5L, "T", "D",
                new java.util.ArrayList<>(java.util.Arrays.asList(1, 2, 2, 0, null)));

        verify(repo, org.mockito.Mockito.times(2)).saveAndFlush(any());
        verify(push, org.mockito.Mockito.times(2)).emitir(anyList(), any());
    }

    @Test
    @DisplayName("publicar duplicado: no lanza, no emite")
    void publicar_duplicadoSeOmite() {
        when(repo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq"));

        service.publicar("VENTA_TICKET", "VENTA", 5L, "T", "D", List.of(1));

        verify(push, never()).emitir(anyList(), any());
    }

    @Test
    @DisplayName("publicarParaGerencia: resuelve GERENTE/ADMINISTRADOR")
    void publicarParaGerencia_resuelveIds() {
        when(destinatarioRepo.findGerenteAdminIds()).thenReturn(List.of(3));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.publicarParaGerencia("COBRANZA", "COBRANZA", 9L, "T", "D");

        ArgumentCaptor<NotificacionBandeja> cap = ArgumentCaptor.forClass(NotificacionBandeja.class);
        verify(repo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getUsuarioId()).isEqualTo(3);
    }

    @Test
    @DisplayName("noLeidas: delega el conteo")
    void noLeidas_delega() {
        when(repo.countByUsuarioIdAndLeidaEnIsNull(7)).thenReturn(4L);

        assertThat(service.noLeidas(7).noLeidas()).isEqualTo(4L);
    }

    @Test
    @DisplayName("marcarLeida: marca y devuelve")
    void marcarLeida_ok() {
        when(repo.findByBandejaIdAndUsuarioId(9L, 7))
                .thenReturn(Optional.of(fila(7, "VENTA_TICKET")));

        var r = service.marcarLeida(7, 9L);

        assertThat(r.bandejaId()).isEqualTo(1L);
        verify(repo).save(any());
    }

    @Test
    @DisplayName("marcarLeida de otro usuario: 404")
    void marcarLeida_ajena404() {
        when(repo.findByBandejaIdAndUsuarioId(9L, 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarLeida(7, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("listar: pagina más recientes primero")
    void listar_pagina() {
        when(repo.findByUsuarioIdOrderByCreadaEnDesc(7, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(fila(7, "VENTA_TICKET"))));

        var page = service.listar(7, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).tipo()).isEqualTo("VENTA_TICKET");
    }
}
