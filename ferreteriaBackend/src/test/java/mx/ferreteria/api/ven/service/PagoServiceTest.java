package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.CuentaCobrar;
import mx.ferreteria.api.ven.entity.PagoCliente;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock PagoClienteRepository repo;
    @Mock CuentaCobrarRepository cuentaRepo;

    @InjectMocks
    PagoService service;

    // ── helpers ──────────────────────────────────────────────────────

    private CuentaCobrar sampleCuenta(Long id) {
        return CuentaCobrar.builder()
                .cuentaCobrarId(id).ventaId(1L).clienteId(1L)
                .montoTotal(new BigDecimal("116.00")).montoPagado(BigDecimal.ZERO)
                .fechaVencimiento(LocalDate.now().plusDays(15))
                .estado("VIGENTE").creadoEn(Instant.now()).build();
    }

    private PagoCliente samplePago(Long id) {
        return PagoCliente.builder()
                .pagoClienteId(id).cuentaCobrarId(1L).formaPagoId(1)
                .monto(new BigDecimal("100.00")).fecha(Instant.now()).usuarioId(1)
                .build();
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create ok: cuenta encontrada, guarda pago y retorna PagoResponse")
    void create_ok() {
        when(cuentaRepo.findById(1L)).thenReturn(Optional.of(sampleCuenta(1L)));
        when(repo.save(any(PagoCliente.class))).thenReturn(samplePago(10L));

        VenDtos.PagoClienteRequest req = new VenDtos.PagoClienteRequest(
                1L, 1, new BigDecimal("100.00"), "REF-001", null);

        var resp = service.create(req);

        assertThat(resp.pagoClienteId()).isEqualTo(10L);
        assertThat(resp.formaPagoId()).isEqualTo(1);
        assertThat(resp.monto()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(repo).save(any(PagoCliente.class));
    }

    @Test
    @DisplayName("create cuenta no encontrada: lanza RecursoNoEncontradoException")
    void create_cuentaNotFound() {
        when(cuentaRepo.findById(999L)).thenReturn(Optional.empty());

        VenDtos.PagoClienteRequest req = new VenDtos.PagoClienteRequest(
                999L, 1, new BigDecimal("50.00"), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
