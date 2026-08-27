package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioRequest;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.MovimientoInventario;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.MovimientoInventarioRepository;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

    @Mock
    MovimientoInventarioRepository repo;

    @Mock
    ProductoRepository productoRepo;

    @Mock
    AlmacenRepository almacenRepo;

    @InjectMocks
    MovimientoService service;

    private Producto sampleProducto(Long id, String nombre) {
        return Producto.builder()
                .productoId(id)
                .codigo("P" + id)
                .nombre(nombre)
                .build();
    }

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder()
                .almacenId(id)
                .nombre(nombre)
                .build();
    }

    private MovimientoInventario sampleMovimiento(Long id, Long productoId, Integer almacenId, String tipo) {
        return MovimientoInventario.builder()
                .movimientoId(id)
                .productoId(productoId)
                .almacenId(almacenId)
                .tipo(tipo)
                .cantidad(new BigDecimal("10.000"))
                .motivoId(1)
                .build();
    }

    // ── listByProducto ──────────────────────────────────────────────

    @Test
    @DisplayName("listByProducto: retorna pagina de movimientos con nombres resueltos")
    void listByProducto_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        MovimientoInventario m = sampleMovimiento(1L, 1L, 1, "ENTRADA");
        when(repo.findByProductoIdOrderByCreadoEnDesc(1L, pg))
                .thenReturn(new PageImpl<>(List.of(m), pg, 1));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));
        when(almacenRepo.findAllById(List.of(1)))
                .thenReturn(List.of(sampleAlmacen(1, "Central")));

        var result = service.listByProducto(1L, pg);

        assertThat(result.getContent()).hasSize(1);
        MovimientoInventarioResponse resp = result.getContent().get(0);
        assertThat(resp.productoId()).isEqualTo(1L);
        assertThat(resp.productoNombre()).isEqualTo("Tornillo");
        assertThat(resp.almacenNombre()).isEqualTo("Central");
    }

    // ── listByAlmacen ───────────────────────────────────────────────

    @Test
    @DisplayName("listByAlmacen: retorna pagina de movimientos")
    void listByAlmacen_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        MovimientoInventario m = sampleMovimiento(1L, 1L, 1, "SALIDA");
        when(repo.findByAlmacenIdOrderByCreadoEnDesc(1, pg))
                .thenReturn(new PageImpl<>(List.of(m), pg, 1));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Clavo")));
        when(almacenRepo.findAllById(List.of(1)))
                .thenReturn(List.of(sampleAlmacen(1, "Central")));

        var result = service.listByAlmacen(1, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).tipo()).isEqualTo("SALIDA");
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create ENTRADA: guarda y retorna respuesta")
    void create_entrada() {
        MovimientoInventarioRequest req = new MovimientoInventarioRequest(
                1L, 1, "ENTRADA", new BigDecimal("5.000"),
                new BigDecimal("12.50"), 1, null, null, null);
        when(productoRepo.findById(1L)).thenReturn(Optional.of(sampleProducto(1L, "Tornillo")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));
        MovimientoInventario saved = sampleMovimiento(10L, 1L, 1, "ENTRADA");
        when(repo.save(any(MovimientoInventario.class))).thenReturn(saved);

        MovimientoInventarioResponse resp = service.create(req);

        assertThat(resp.movimientoId()).isEqualTo(10L);
        assertThat(resp.tipo()).isEqualTo("ENTRADA");
        assertThat(resp.productoNombre()).isEqualTo("Tornillo");
        verify(repo).save(any(MovimientoInventario.class));
    }

    @Test
    @DisplayName("create SALIDA: guarda y retorna respuesta")
    void create_salida() {
        MovimientoInventarioRequest req = new MovimientoInventarioRequest(
                1L, 1, "SALIDA", new BigDecimal("3.000"),
                new BigDecimal("12.50"), 1, null, null, null);
        when(productoRepo.findById(1L)).thenReturn(Optional.of(sampleProducto(1L, "Tornillo")));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));
        MovimientoInventario saved = MovimientoInventario.builder()
                .movimientoId(11L).productoId(1L).almacenId(1).tipo("SALIDA")
                .cantidad(new BigDecimal("3.000")).costoUnitario(new BigDecimal("12.50")).motivoId(1)
                .build();
        when(repo.save(any(MovimientoInventario.class))).thenReturn(saved);

        MovimientoInventarioResponse resp = service.create(req);

        assertThat(resp.tipo()).isEqualTo("SALIDA");
        assertThat(resp.cantidad()).isEqualByComparingTo(new BigDecimal("3.000"));
    }

    @Test
    @DisplayName("create tipo invalido: ReglaNegocioException")
    void create_invalidTipo() {
        MovimientoInventarioRequest req = new MovimientoInventarioRequest(
                1L, 1, "X", new BigDecimal("1.000"),
                null, 1, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("create producto inexistente: RecursoNoEncontradoException")
    void create_productoNotFound() {
        MovimientoInventarioRequest req = new MovimientoInventarioRequest(
                999L, 1, "ENTRADA", new BigDecimal("1.000"),
                null, 1, null, null, null);
        when(productoRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create almacen inexistente: RecursoNoEncontradoException")
    void create_almacenNotFound() {
        MovimientoInventarioRequest req = new MovimientoInventarioRequest(
                1L, 999, "ENTRADA", new BigDecimal("1.000"),
                null, 1, null, null, null);
        when(productoRepo.findById(1L)).thenReturn(Optional.of(sampleProducto(1L, "Tornillo")));
        when(almacenRepo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
