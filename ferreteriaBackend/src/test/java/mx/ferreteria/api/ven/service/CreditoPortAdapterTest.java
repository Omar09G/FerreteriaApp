package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.ven.entity.LineaCredito;
import mx.ferreteria.api.ven.repo.LineaCreditoRepository;

@ExtendWith(MockitoExtension.class)
class CreditoPortAdapterTest {

    @Mock
    LineaCreditoRepository lineaRepo;

    @InjectMocks
    CreditoPortAdapter adapter;

    @Test
    @DisplayName("límite nulo o ≤0: no-op sin tocar el repo")
    void limiteNoPositivo_noop() {
        adapter.sincronizarLinea(1L, BigDecimal.ZERO, 30);
        adapter.sincronizarLinea(1L, null, 30);
        adapter.sincronizarLinea(1L, new BigDecimal("-5"), 30);

        verify(lineaRepo, never()).save(any());
        verify(lineaRepo, never()).findByClienteIdAndEstado(any(), any());
    }

    @Test
    @DisplayName("línea ACTIVA existente: actualiza monto y días")
    void existente_actualiza() {
        LineaCredito existente = LineaCredito.builder().lineaCreditoId(3L).clienteId(1L)
                .montoAutorizado(BigDecimal.ONE).diasCredito((short) 10).estado("ACTIVA").build();
        when(lineaRepo.findByClienteIdAndEstado(1L, "ACTIVA")).thenReturn(Optional.of(existente));

        adapter.sincronizarLinea(1L, new BigDecimal("50000.00"), 30);

        assertThat(existente.getMontoAutorizado()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(existente.getDiasCredito()).isEqualTo((short) 30);
        verify(lineaRepo).save(existente);
    }

    @Test
    @DisplayName("sin línea: crea ACTIVA con defaults (días null→15, actor 1 sin sesión)")
    void inexistente_crea() {
        when(lineaRepo.findByClienteIdAndEstado(1L, "ACTIVA")).thenReturn(Optional.empty());

        adapter.sincronizarLinea(1L, new BigDecimal("50000.00"), null);

        ArgumentCaptor<LineaCredito> cap = ArgumentCaptor.forClass(LineaCredito.class);
        verify(lineaRepo).save(cap.capture());
        LineaCredito creada = cap.getValue();
        assertThat(creada.getClienteId()).isEqualTo(1L);
        assertThat(creada.getMontoAutorizado()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(creada.getDiasCredito()).isEqualTo((short) 15);
        assertThat(creada.getEstado()).isEqualTo("ACTIVA");
        assertThat(creada.getTasaMoratorio()).isEqualTo(BigDecimal.ZERO);
        assertThat(creada.getUsuarioAutorizoId()).isEqualTo(1);
    }

    @Test
    @DisplayName("días fuera de rango se acotan a 1..365 (rango del trigger)")
    void dias_seAcotan() {
        when(lineaRepo.findByClienteIdAndEstado(1L, "ACTIVA")).thenReturn(Optional.empty());

        adapter.sincronizarLinea(1L, BigDecimal.TEN, 500);

        ArgumentCaptor<LineaCredito> cap = ArgumentCaptor.forClass(LineaCredito.class);
        verify(lineaRepo).save(cap.capture());
        assertThat(cap.getValue().getDiasCredito()).isEqualTo((short) 365);
    }
}
