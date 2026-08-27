package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.ven.entity.CuentaCobrar;
import mx.ferreteria.api.ven.entity.PagoCliente;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditoServiceTest {

    @Mock CuentaCobrarRepository cuentaRepo;
    @Mock PagoClienteRepository pagoRepo;
    @Mock VentaRepository ventaRepo;
    @Mock ClienteRepository clienteRepo;

    @InjectMocks
    CreditoService service;

    // ── helpers ──────────────────────────────────────────────────────

    private CuentaCobrar sampleCuenta(Long id, String estado) {
        return CuentaCobrar.builder()
                .cuentaCobrarId(id).ventaId(1L).clienteId(1L)
                .montoTotal(new BigDecimal("116.00")).montoPagado(BigDecimal.ZERO)
                .fechaVencimiento(LocalDate.now().plusDays(15)).estado(estado)
                .creadoEn(Instant.now()).build();
    }

    private void stubToResponse() {
        when(clienteRepo.findById(1L))
                .thenReturn(Optional.of(Cliente.builder().clienteId(1L).razonSocial("Maria Lopez").build()));
        when(ventaRepo.findById(1L))
                .thenReturn(Optional.of(Venta.builder()
                        .ventaId(1L).folio("V-001").build()));
        when(pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(anyLong())).thenReturn(List.of());
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── listCuentas ─────────────────────────────────────────────────

    @Test
    @DisplayName("listCuentas sin estado: findAll retorna pagina con items")
    void listCuentas_all() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentas(null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).cuentaCobrarId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listCuentas por estado: filtrado retorna items")
    void listCuentas_byEstado() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.findByEstadoOrderByCreadoEnDesc("VIGENTE", pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentas("VIGENTE", pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }

    // ── listCuentasByCliente ────────────────────────────────────────

    @Test
    @DisplayName("listCuentasByCliente sin estado: retorna cuentas del cliente")
    void listCuentasByCliente_all() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.findByClienteIdOrderByCreadoEnDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentasByCliente(1L, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).clienteNombre()).isEqualTo("Maria Lopez");
    }

    @Test
    @DisplayName("listCuentasByCliente con estado: retorna cuentas filtradas")
    void listCuentasByCliente_byEstado() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.findByClienteIdAndEstadoOrderByCreadoEnDesc(1L, "VIGENTE", pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentasByCliente(1L, "VIGENTE", pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }
}
