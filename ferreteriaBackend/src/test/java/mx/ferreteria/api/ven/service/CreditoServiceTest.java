package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    CuentaCobrarRepository cuentaRepo;
    @Mock
    PagoClienteRepository pagoRepo;
    @Mock
    VentaRepository ventaRepo;
    @Mock
    ClienteRepository clienteRepo;

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

    private CuentaCobrar sampleCuenta(Long id, Long ventaId, Long clienteId,
            String estado, BigDecimal pagado) {
        return CuentaCobrar.builder()
                .cuentaCobrarId(id).ventaId(ventaId).clienteId(clienteId)
                .montoTotal(new BigDecimal("116.00")).montoPagado(pagado)
                .fechaVencimiento(LocalDate.now().plusDays(15)).estado(estado)
                .creadoEn(Instant.now()).build();
    }

    private PagoCliente samplePago(Long pagoId, Long cuentaId, Instant fecha, String monto) {
        return PagoCliente.builder()
                .pagoClienteId(pagoId).cuentaCobrarId(cuentaId)
                .formaPagoId(1).referencia("REF-" + pagoId)
                .monto(new BigDecimal(monto)).fecha(fecha)
                .usuarioId(7).build();
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── listCuentas ─────────────────────────────────────────────────

    @Test
    @DisplayName("listCuentas sin filtros: filtrar retorna pagina con items")
    void listCuentas_all() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).cuentaCobrarId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listCuentas por estado: filtrar retorna items")
    void listCuentas_byEstado() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(null, "VIGENTE", null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentas(null, null, "VIGENTE", pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }

    // ── listCuentasByCliente ────────────────────────────────────────

    @Test
    @DisplayName("listCuentasByCliente sin estado: retorna cuentas del cliente")
    void listCuentasByCliente_all() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(1L, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentasByCliente(1L, null, null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).clienteNombre()).isEqualTo("Maria Lopez");
    }

    @Test
    @DisplayName("listCuentasByCliente con estado: retorna cuentas filtradas")
    void listCuentasByCliente_byEstado() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(1L, "VIGENTE", null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentasByCliente(1L, null, null, "VIGENTE", pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }

    // ── ruta single: pagina vacia y datos faltantes ──────────────────

    @Test
    @DisplayName("listCuentas pagina vacia: retorna vacia sin tocar otros repos")
    void listCuentas_emptyPage() {
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(), pg(), 0));

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verifyNoInteractions(clienteRepo, ventaRepo, pagoRepo);
    }

    @Test
    @DisplayName("single con clienteId null: clienteNombre null sin buscar cliente")
    void single_clienteIdNull() {
        CuentaCobrar cc = sampleCuenta(1L, 1L, null, "VIGENTE", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        when(ventaRepo.findById(1L))
                .thenReturn(Optional.of(Venta.builder().ventaId(1L).folio("V-001").build()));
        when(pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(1L)).thenReturn(List.of());

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent().get(0).clienteNombre()).isNull();
        assertThat(result.getContent().get(0).ventaFolio()).isEqualTo("V-001");
        verify(clienteRepo, never()).findById(any());
    }

    @Test
    @DisplayName("single con cliente ausente: clienteNombre null")
    void single_clienteNotFound() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        when(clienteRepo.findById(1L)).thenReturn(Optional.empty());
        when(ventaRepo.findById(1L))
                .thenReturn(Optional.of(Venta.builder().ventaId(1L).folio("V-001").build()));
        when(pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(1L)).thenReturn(List.of());

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent().get(0).clienteNombre()).isNull();
    }

    @Test
    @DisplayName("single con venta ausente: ventaFolio null")
    void single_ventaNotFound() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();
        when(ventaRepo.findById(1L)).thenReturn(Optional.empty());

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent().get(0).ventaFolio()).isNull();
        assertThat(result.getContent().get(0).clienteNombre()).isEqualTo("Maria Lopez");
    }

    @Test
    @DisplayName("single con pagos: mapea PagoResponse y calcula saldo")
    void single_conPagos() {
        CuentaCobrar cc = sampleCuenta(1L, 1L, 1L, "VIGENTE", new BigDecimal("16.00"));
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();
        Instant fecha = Instant.parse("2026-09-01T10:00:00Z");
        when(pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(1L))
                .thenReturn(List.of(samplePago(9L, 1L, fecha, "16.00")));

        var result = service.listCuentas(null, null, null, pg());

        var resp = result.getContent().get(0);
        assertThat(resp.saldo()).isEqualByComparingTo("100.00");
        assertThat(resp.pagos()).hasSize(1);
        assertThat(resp.pagos().get(0).pagoClienteId()).isEqualTo(9L);
        assertThat(resp.pagos().get(0).referencia()).isEqualTo("REF-9");
        assertThat(resp.pagos().get(0).monto()).isEqualByComparingTo("16.00");
        assertThat(resp.pagos().get(0).fecha()).isEqualTo(fecha);
    }

    @Test
    @DisplayName("listCuentasByCliente pasa rango de fechas y estado al filtrar")
    void byCliente_pasaFiltros() {
        CuentaCobrar cc = sampleCuenta(1L, "VIGENTE");
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 12, 31);
        when(cuentaRepo.filtrar(1L, "VENCIDA", desde, hasta, pg()))
                .thenReturn(new PageImpl<>(List.of(cc), pg(), 1));
        stubToResponse();

        var result = service.listCuentasByCliente(1L, desde, hasta, "VENCIDA", pg());

        assertThat(result.getContent()).hasSize(1);
        verify(cuentaRepo).filtrar(1L, "VENCIDA", desde, hasta, pg());
    }

    // ── ruta batch (2+ elementos) ────────────────────────────────────

    @Test
    @DisplayName("batch: resuelve nombres, folios, agrupa pagos ordenados desc y calcula saldo")
    void batch_resuelveTodo() {
        CuentaCobrar c1 = sampleCuenta(1L, 10L, 1L, "VIGENTE", new BigDecimal("16.00"));
        CuentaCobrar c2 = sampleCuenta(2L, 20L, 2L, "VENCIDA", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(clienteRepo.findAllById(anySet())).thenReturn(List.of(
                Cliente.builder().clienteId(1L).razonSocial("Maria Lopez").build(),
                Cliente.builder().clienteId(2L).razonSocial("Juan Perez").build()));
        when(ventaRepo.findAllById(anySet())).thenReturn(List.of(
                Venta.builder().ventaId(10L).folio("V-010").build(),
                Venta.builder().ventaId(20L).folio("V-020").build()));
        Instant viejo = Instant.parse("2026-08-01T10:00:00Z");
        Instant nuevo = Instant.parse("2026-09-01T10:00:00Z");
        when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of(
                samplePago(11L, 1L, viejo, "10.00"),
                samplePago(12L, 1L, nuevo, "6.00")));

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        var r1 = result.getContent().get(0);
        assertThat(r1.clienteNombre()).isEqualTo("Maria Lopez");
        assertThat(r1.ventaFolio()).isEqualTo("V-010");
        assertThat(r1.saldo()).isEqualByComparingTo("100.00");
        assertThat(r1.pagos()).extracting(p -> p.pagoClienteId())
                .containsExactly(12L, 11L);
        var r2 = result.getContent().get(1);
        assertThat(r2.clienteNombre()).isEqualTo("Juan Perez");
        assertThat(r2.ventaFolio()).isEqualTo("V-020");
        assertThat(r2.pagos()).isEmpty();
    }

    @Test
    @DisplayName("batch con cliente y venta ausentes: nombres y folios null")
    void batch_datosFaltantes() {
        CuentaCobrar c1 = sampleCuenta(1L, 10L, 1L, "VIGENTE", BigDecimal.ZERO);
        CuentaCobrar c2 = sampleCuenta(2L, 20L, 2L, "VIGENTE", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(clienteRepo.findAllById(anySet()))
                .thenReturn(List.of(Cliente.builder().clienteId(1L).razonSocial("Maria Lopez").build()));
        when(ventaRepo.findAllById(anySet())).thenReturn(List.of());
        when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of());

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent().get(0).clienteNombre()).isEqualTo("Maria Lopez");
        assertThat(result.getContent().get(0).ventaFolio()).isNull();
        assertThat(result.getContent().get(1).clienteNombre()).isNull();
        assertThat(result.getContent().get(1).ventaFolio()).isNull();
        assertThat(result.getContent().get(1).pagos()).isEmpty();
    }

    @Test
    @DisplayName("batch con clienteId null en todas: no busca clientes")
    void batch_clienteIdsNulos() {
        CuentaCobrar c1 = sampleCuenta(1L, 10L, null, "VIGENTE", BigDecimal.ZERO);
        CuentaCobrar c2 = sampleCuenta(2L, 20L, null, "VIGENTE", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(ventaRepo.findAllById(anySet())).thenReturn(List.of(
                Venta.builder().ventaId(10L).folio("V-010").build(),
                Venta.builder().ventaId(20L).folio("V-020").build()));
        when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of());

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent()).extracting(r -> r.clienteNombre())
                .containsExactly(null, null);
        verify(clienteRepo, never()).findAllById(any());
    }

    @Test
    @DisplayName("batch: pago con fecha null queda primero por reversed de nullsLast")
    void batch_pagoFechaNullPrimero() {
        CuentaCobrar c1 = sampleCuenta(1L, 10L, 1L, "VIGENTE", BigDecimal.ZERO);
        CuentaCobrar c2 = sampleCuenta(2L, 20L, 2L, "VIGENTE", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(null, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(clienteRepo.findAllById(anySet())).thenReturn(List.of());
        when(ventaRepo.findAllById(anySet())).thenReturn(List.of());
        when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of(
                samplePago(21L, 1L, Instant.parse("2026-09-01T10:00:00Z"), "5.00"),
                samplePago(22L, 1L, null, "7.00")));

        var result = service.listCuentas(null, null, null, pg());

        assertThat(result.getContent().get(0).pagos())
                .extracting(p -> p.pagoClienteId())
                .containsExactly(22L, 21L);
    }

    @Test
    @DisplayName("listCuentasByCliente batch: usa ruta batch con clienteId")
    void byCliente_batch() {
        CuentaCobrar c1 = sampleCuenta(1L, 10L, 5L, "VIGENTE", BigDecimal.ZERO);
        CuentaCobrar c2 = sampleCuenta(2L, 20L, 5L, "VIGENTE", BigDecimal.ZERO);
        when(cuentaRepo.filtrar(5L, null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(clienteRepo.findAllById(anySet())).thenReturn(List.of(
                Cliente.builder().clienteId(5L).razonSocial("Acme SA").build()));
        when(ventaRepo.findAllById(anySet())).thenReturn(List.of(
                Venta.builder().ventaId(10L).folio("V-010").build(),
                Venta.builder().ventaId(20L).folio("V-020").build()));
        when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of());

        var result = service.listCuentasByCliente(5L, null, null, null, pg());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(r -> r.clienteNombre())
                .containsExactly("Acme SA", "Acme SA");
        verify(cuentaRepo).filtrar(5L, null, null, null, pg());
    }
}
