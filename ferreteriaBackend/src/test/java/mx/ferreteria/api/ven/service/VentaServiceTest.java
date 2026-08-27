package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.PagoCliente;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

    @Mock VentaRepository ventaRepo;
    @Mock VentaDetalleRepository detalleRepo;
    @Mock PagoClienteRepository pagoRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock ClienteRepository clienteRepo;
    @Mock ProductoRepository productoRepo;
    @Mock FormaPagoRepository formaPagoRepo;

    @InjectMocks
    VentaService service;

    // ── helpers ──────────────────────────────────────────────────────

    private Venta sampleVenta(Long id, String folio, String estado) {
        return Venta.builder()
                .ventaId(id).folio(folio).almacenId(1).formaPagoId(1)
                .subtotal(new BigDecimal("100.00")).iva(new BigDecimal("16.00"))
                .total(new BigDecimal("116.00")).estado(estado).usuarioId(1)
                .ivaTasa(new BigDecimal("16.00")).ivaIncluido(true)
                .descuentoTotal(BigDecimal.ZERO).fecha(Instant.now())
                .build();
    }

    private VentaDetalle sampleDetalle(Long id, Long ventaId, Long productoId) {
        return VentaDetalle.builder()
                .ventaDetalleId(id).ventaId(ventaId).productoId(productoId)
                .cantidad(new BigDecimal("2.000")).precioUnitario(new BigDecimal("50.00"))
                .costoUnitario(BigDecimal.ZERO).descuentoLinea(BigDecimal.ZERO)
                .build();
    }

    private void stubToResponse() {
        when(almacenRepo.findById(1))
                .thenReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
        when(formaPagoRepo.findById(1))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(detalleRepo.findByVentaId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Martillo").build()));
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin filtros: findAll retorna pagina con items")
    void list_all() {
        Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
        when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
        stubToResponse();

        var result = service.list(null, null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).ventaId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("list por almacen y rango: findByAlmacenIdAndFechaBetween retorna filtrado")
    void list_byAlmacen() {
        Instant desde = Instant.parse("2025-01-01T00:00:00Z");
        Instant hasta = Instant.parse("2025-12-31T23:59:59Z");
        Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
        when(ventaRepo.findByAlmacenIdAndFechaBetweenOrderByFechaDesc(1, desde, hasta, pg()))
                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
        stubToResponse();

        var result = service.list(1, desde, hasta, pg());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("list por rango de fechas: findByFechaBetween retorna filtrado")
    void list_byDateRange() {
        Instant desde = Instant.parse("2025-01-01T00:00:00Z");
        Instant hasta = Instant.parse("2025-12-31T23:59:59Z");
        Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
        when(ventaRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta, pg()))
                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
        stubToResponse();

        var result = service.list(null, desde, hasta, pg());

        assertThat(result.getContent()).hasSize(1);
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna VentaResponse con nombres resueltos")
    void getById_found() {
        Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        stubToResponse();

        var resp = service.getById(1L);

        assertThat(resp.ventaId()).isEqualTo(1L);
        assertThat(resp.almacenNombre()).isEqualTo("Almacen Central");
        assertThat(resp.formaPagoNombre()).isEqualTo("EFECTIVO");
    }

    @Test
    @DisplayName("getById inexistente: lanza RecursoNoEncontradoException")
    void getById_notFound() {
        when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── checkout ────────────────────────────────────────────────────

    @Test
    @DisplayName("checkout ok: guarda venta, detalles y pagos")
    void checkout_ok() {
        Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
        when(almacenRepo.findById(1))
                .thenReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
        when(formaPagoRepo.findById(1))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(ventaRepo.save(any(Venta.class))).thenReturn(saved);
        when(ventaRepo.findById(10L)).thenReturn(Optional.of(saved));
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(detalleRepo.findByVentaId(10L)).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Martillo").build()));

        VenDtos.VentaRequest req = new VenDtos.VentaRequest(
                1, null, null, 1,
                List.of(new VenDtos.VentaDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("50.00"))),
                List.of(new VenDtos.PagoRequest(1, new BigDecimal("116.00"), null)),
                null);

        var resp = service.checkout(req);

        assertThat(resp.ventaId()).isEqualTo(10L);
        verify(detalleRepo).save(any(VentaDetalle.class));
        verify(pagoRepo).save(any(PagoCliente.class));
        verify(ventaRepo).flush();
    }

    @Test
    @DisplayName("checkout almacen no encontrado: lanza RecursoNoEncontradoException")
    void checkout_almacenNotFound() {
        when(almacenRepo.findById(99)).thenReturn(Optional.empty());

        VenDtos.VentaRequest req = new VenDtos.VentaRequest(
                99, null, null, 1,
                List.of(new VenDtos.VentaDetalleRequest(1L, new BigDecimal("1.000"), new BigDecimal("10.00"))),
                List.of(new VenDtos.PagoRequest(1, new BigDecimal("10.00"), null)),
                null);

        assertThatThrownBy(() -> service.checkout(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(detalleRepo, never()).save(any());
    }

    // ── cancel ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel ok: venta activa se marca como CANCELADA")
    void cancel_ok() {
        Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        stubToResponse();

        var resp = service.cancel(1L, "Cliente solicitó");

        assertThat(v.getEstado()).isEqualTo("CANCELADA");
        verify(ventaRepo).save(v);
        assertThat(resp.estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancel ya cancelada: lanza ReglaNegocioException")
    void cancel_alreadyCancelled() {
        Venta v = sampleVenta(1L, "V-001", "CANCELADA");
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.cancel(1L, "Motivo"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(ventaRepo, never()).save(any());
    }

    @Test
    @DisplayName("cancel inexistente: lanza RecursoNoEncontradoException")
    void cancel_notFound() {
        when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(999L, "Motivo"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
