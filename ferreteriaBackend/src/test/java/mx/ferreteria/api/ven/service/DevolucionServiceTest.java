package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.DevolucionDetalle;
import mx.ferreteria.api.ven.entity.DevolucionVenta;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.repo.DevolucionDetalleRepository;
import mx.ferreteria.api.ven.repo.DevolucionVentaRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevolucionServiceTest {

    @Mock DevolucionVentaRepository repo;
    @Mock DevolucionDetalleRepository detalleRepo;
    @Mock VentaRepository ventaRepo;
    @Mock ProductoRepository productoRepo;
    @Mock FormaPagoRepository formaPagoRepo;

    @InjectMocks
    DevolucionService service;

    // ── helpers ──────────────────────────────────────────────────────

    private DevolucionVenta sampleDevolucion(Long id) {
        return DevolucionVenta.builder()
                .devolucionId(id).ventaId(1L).motivo("Defectuoso")
                .total(BigDecimal.ZERO).formaDevolucionId(1).usuarioId(1)
                .fecha(Instant.now()).build();
    }

    private Venta sampleVenta(String estado) {
        return Venta.builder().ventaId(1L).folio("V-001").estado(estado)
                .almacenId(1).formaPagoId(1).subtotal(BigDecimal.ZERO)
                .iva(BigDecimal.ZERO).total(BigDecimal.ZERO).usuarioId(1)
                .ivaTasa(new BigDecimal("16.00")).ivaIncluido(true)
                .descuentoTotal(BigDecimal.ZERO).fecha(Instant.now()).build();
    }

    private void stubToResponse() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("COMPLETADA")));
        when(formaPagoRepo.findById(1))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(detalleRepo.findByDevolucionId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Clavo").build()));
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── listByVenta ─────────────────────────────────────────────────

    @Test
    @DisplayName("listByVenta: retorna pagina de devoluciones de una venta")
    void listByVenta() {
        DevolucionVenta d = sampleDevolucion(1L);
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(d), pg(), 1));
        stubToResponse();

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).devolucionId()).isEqualTo(1L);
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna DevolucionResponse con detalles")
    void getById_found() {
        DevolucionVenta d = sampleDevolucion(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        stubToResponse();

        var resp = service.getById(1L);

        assertThat(resp.devolucionId()).isEqualTo(1L);
        assertThat(resp.motivo()).isEqualTo("Defectuoso");
        assertThat(resp.ventaFolio()).isEqualTo("V-001");
    }

    @Test
    @DisplayName("getById inexistente: lanza RecursoNoEncontradoException")
    void getById_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create ok: venta activa, guarda devolucion y detalles")
    void create_ok() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("COMPLETADA")));
        DevolucionVenta saved = sampleDevolucion(10L);
        when(repo.save(any(DevolucionVenta.class))).thenReturn(saved);
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
        stubToResponse();

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Producto defectuoso", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, 1L, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        var resp = service.create(req);

        assertThat(resp.devolucionId()).isEqualTo(10L);
        verify(repo).save(any(DevolucionVenta.class));
        verify(detalleRepo).save(any(DevolucionDetalle.class));
    }

    @Test
    @DisplayName("create venta no encontrada: lanza RecursoNoEncontradoException")
    void create_ventaNotFound() {
        when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                999L, "Motivo", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, null, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create venta cancelada: lanza ReglaNegocioException")
    void create_ventaCancelled() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("CANCELADA")));

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Motivo", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, null, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
