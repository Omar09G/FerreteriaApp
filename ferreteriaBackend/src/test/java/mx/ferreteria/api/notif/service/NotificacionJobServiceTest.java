package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionJobServiceTest {

    @Mock
    NotificacionJobRepository repo;

    @InjectMocks
    NotificacionJobService service;

    private static NotificacionJob job(Long jobId, String estado) {
        return NotificacionJob.builder().jobId(jobId)
                .tipo(NotificacionJob.TIPO_VENTA_TICKET).refTipo(NotificacionJob.REF_VENTA)
                .refId(10L).estado(estado).intentos(0).build();
    }

    @Test
    @DisplayName("crearVentaTicket nuevo: guarda en PENDIENTE con unique (tipo, ref)")
    void crear_nuevoGuardaPendiente() {
        when(repo.findByTipoAndRefId(NotificacionJob.TIPO_VENTA_TICKET, 10L))
                .thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(NotificacionJob.class))).thenAnswer(inv -> {
            NotificacionJob j = inv.getArgument(0);
            j.setJobId(11L);
            return j;
        });

        NotificacionJob saved = service.crearVentaTicket(10L);

        assertThat(saved.getJobId()).isEqualTo(11L);
        assertThat(saved.getEstado()).isEqualTo(NotificacionJob.ESTADO_PENDIENTE);
        assertThat(saved.getTipo()).isEqualTo(NotificacionJob.TIPO_VENTA_TICKET);
        verify(repo).saveAndFlush(any(NotificacionJob.class));
    }

    @Test
    @DisplayName("crearVentaTicket existente: idempotente, no vuelve a guardar")
    void crear_existenteIdempotente() {
        when(repo.findByTipoAndRefId(NotificacionJob.TIPO_VENTA_TICKET, 10L))
                .thenReturn(Optional.of(job(11L, NotificacionJob.ESTADO_ENVIADA)));

        NotificacionJob existing = service.crearVentaTicket(10L);

        assertThat(existing.getJobId()).isEqualTo(11L);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("marcarEnviada: estado + pdf + timestamp")
    void marcarEnviada_transiciona() {
        NotificacionJob j = job(11L, NotificacionJob.ESTADO_PROCESANDO);

        service.marcarEnviada(j, "tickets/10.pdf");

        assertThat(j.getEstado()).isEqualTo(NotificacionJob.ESTADO_ENVIADA);
        assertThat(j.getPdfUrl()).isEqualTo("tickets/10.pdf");
        assertThat(j.getEnviadoEn()).isNotNull();
        assertThat(j.getUltimoError()).isNull();
        verify(repo).save(j);
    }

    @Test
    @DisplayName("marcarError: trunca el mensaje a 500 caracteres")
    void marcarError_trunca() {
        NotificacionJob j = job(11L, NotificacionJob.ESTADO_PROCESANDO);

        service.marcarError(j, "x".repeat(600));

        assertThat(j.getEstado()).isEqualTo(NotificacionJob.ESTADO_ERROR);
        assertThat(j.getUltimoError()).hasSize(500);
        verify(repo).save(j);
    }

    @Test
    @DisplayName("marcarProcesando: incrementa intentos y limpia error previo")
    void marcarProcesando_incrementa() {
        NotificacionJob j = job(11L, NotificacionJob.ESTADO_PENDIENTE);
        j.setUltimoError("boom");

        service.marcarProcesando(j);

        assertThat(j.getEstado()).isEqualTo(NotificacionJob.ESTADO_PROCESANDO);
        assertThat(j.getIntentos()).isEqualTo(1);
        assertThat(j.getUltimoError()).isNull();
    }
}
