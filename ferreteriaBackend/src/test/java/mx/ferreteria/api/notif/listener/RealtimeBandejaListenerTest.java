package mx.ferreteria.api.notif.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import mx.ferreteria.api.com.repo.CompraRepository;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.repo.VentaRepository;
import mx.ferreteria.api.ven.service.VentaCanceladaEvent;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealtimeBandejaListenerTest {

    @Mock
    BandejaService bandeja;

    @Mock
    InformeDestinatarioRepository destinatarios;

    @Mock
    VentaRepository ventaRepo;

    @Mock
    CompraRepository compraRepo;

    @Mock
    mx.ferreteria.api.rh.repo.NominaRepository nominaRepo;

    @InjectMocks
    RealtimeBandejaListener listener;

    @Test
    @DisplayName("venta cancelada: avisa a gerencia + vendedor")
    void ventaCancelada_avisaGerenciaYVendedor() {
        Venta v = Venta.builder().ventaId(1L).folio("V-1").usuarioId(7)
                .total(new BigDecimal("100.00")).fecha(Instant.now())
                .formaPagoId(1).almacenId(1).build();
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(destinatarios.findGerenteAdminIds()).thenReturn(new java.util.ArrayList<>(List.of(1, 2)));

        listener.onVentaCancelada(new VentaCanceladaEvent(1L));

        ArgumentCaptor<List<Integer>> dest = ArgumentCaptor.forClass(List.class);
        verify(bandeja).publicar(eq("VENTA_CANCELADA"), eq("VENTA"), eq(1L),
                org.mockito.ArgumentMatchers.contains("V-1"), any(), dest.capture());
        assertThat(dest.getValue()).containsExactlyInAnyOrder(1, 2, 7);
    }

    @Test
    @DisplayName("venta inexistente: no publica")
    void ventaInexistente_noPublica() {
        when(ventaRepo.findById(99L)).thenReturn(Optional.empty());

        listener.onVentaCancelada(new VentaCanceladaEvent(99L));

        verify(bandeja, org.mockito.Mockito.never()).publicar(any(), any(), anyLong(),
                any(), any(), anyCollection());
    }
}
