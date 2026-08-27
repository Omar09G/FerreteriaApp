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

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Cotizacion;
import mx.ferreteria.api.ven.entity.CotizacionDetalle;
import mx.ferreteria.api.ven.repo.CotizacionDetalleRepository;
import mx.ferreteria.api.ven.repo.CotizacionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotizacionServiceTest {

    @Mock CotizacionRepository repo;
    @Mock CotizacionDetalleRepository detalleRepo;
    @Mock ClienteRepository clienteRepo;
    @Mock ProductoRepository productoRepo;

    @InjectMocks
    CotizacionService service;

    // ── helpers ──────────────────────────────────────────────────────

    private Cotizacion sampleCotizacion(Long id, String folio, String estado) {
        return Cotizacion.builder()
                .cotizacionId(id).folio(folio).fecha(Instant.now())
                .subtotal(BigDecimal.ZERO).iva(BigDecimal.ZERO).total(BigDecimal.ZERO)
                .estado(estado).usuarioId(1)
                .build();
    }

    private CotizacionDetalle sampleDetalle(Long cotizacionId, Long productoId) {
        return CotizacionDetalle.builder()
                .cotizacionId(cotizacionId).productoId(productoId)
                .cantidad(new BigDecimal("3.000")).precioUnitario(new BigDecimal("25.00"))
                .build();
    }

    private void stubToResponse() {
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(detalleRepo.findByCotizacionId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Taladro").build()));
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin estado: findAll retorna pagina con items")
    void list_all() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findAll(pg())).thenReturn(new PageImpl<>(List.of(c), pg(), 1));
        stubToResponse();

        var result = service.list(null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).cotizacionId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("list por estado: findByEstadoOrderByFechaDesc retorna filtrado")
    void list_byEstado() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findByEstadoOrderByFechaDesc("VIGENTE", pg()))
                .thenReturn(new PageImpl<>(List.of(c), pg(), 1));
        stubToResponse();

        var result = service.list("VIGENTE", pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna CotizacionResponse con detalles")
    void getById_found() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(productoRepo.findById(1L))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Taladro").build()));
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());

        var resp = service.getById(1L);

        assertThat(resp.cotizacionId()).isEqualTo(1L);
        assertThat(resp.folio()).isEqualTo("COT-001");
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Taladro");
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
    @DisplayName("create ok: guarda entidad y detalles, retorna respuesta")
    void create_ok() {
        Cotizacion saved = sampleCotizacion(5L, "COT-005", "VIGENTE");
        when(repo.save(any(Cotizacion.class))).thenReturn(saved);
        stubToResponse();

        VenDtos.CotizacionRequest req = new VenDtos.CotizacionRequest(
                null, null,
                List.of(new VenDtos.CotizacionDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("30.00"))));

        var resp = service.create(req);

        assertThat(resp.cotizacionId()).isEqualTo(5L);
        verify(repo).save(any(Cotizacion.class));
        verify(detalleRepo).save(any(CotizacionDetalle.class));
    }

    // ── convertirAVenta ─────────────────────────────────────────────

    @Test
    @DisplayName("convertir ok: cotizacion VIGENTE se marca como CONVERTIDA")
    void convertir_ok() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        stubToResponse();

        var resp = service.convertirAVenta(1L, 1, 1);

        assertThat(c.getEstado()).isEqualTo("CONVERTIDA");
        verify(repo).save(c);
        assertThat(resp.estado()).isEqualTo("CONVERTIDA");
    }

    @Test
    @DisplayName("convertir con estado no VIGENTE: lanza ReglaNegocioException")
    void convertir_notVigente() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "CONVERTIDA");
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.convertirAVenta(1L, 1, 1))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("convertir inexistente: lanza RecursoNoEncontradoException")
    void convertir_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convertirAVenta(999L, 1, 1))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
