package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
import org.springframework.jdbc.core.JdbcTemplate;

import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.MovimientoInventario;
import mx.ferreteria.api.inv.entity.Traslado;
import mx.ferreteria.api.inv.entity.TrasladoDetalle;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.MovimientoInventarioRepository;
import mx.ferreteria.api.inv.repo.TrasladoDetalleRepository;
import mx.ferreteria.api.inv.repo.TrasladoRepository;

@ExtendWith(MockitoExtension.class)
class TrasladoServiceTest {

    @Mock
    TrasladoRepository repo;

    @Mock
    TrasladoDetalleRepository detalleRepo;

    @Mock
    MovimientoInventarioRepository movimientoRepo;

    @Mock
    AlmacenRepository almacenRepo;

    @Mock
    ProductoRepository productoRepo;

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    TrasladoService service;

    private Producto sampleProducto(Long id, String nombre) {
        return Producto.builder().productoId(id).codigo("P" + id).nombre(nombre).build();
    }

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder().almacenId(id).nombre(nombre).build();
    }

    private Traslado sampleTraslado(Long id, Integer origen, Integer destino) {
        return Traslado.builder()
                .trasladoId(id)
                .folio("TR-" + id)
                .almacenOrigen(origen)
                .almacenDestino(destino)
                .estado("APLICADO")
                .usuarioId(1)
                .build();
    }

    private TrasladoDetalle sampleDetalle(Long trasladoId, Long productoId, BigDecimal cantidad) {
        return TrasladoDetalle.builder()
                .trasladoId(trasladoId)
                .productoId(productoId)
                .cantidad(cantidad)
                .build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: retorna pagina de traslados con detalles")
    void list_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Traslado t = sampleTraslado(1L, 1, 2);
        when(repo.findAllByOrderByCreadoEnDesc(pg))
                .thenReturn(new PageImpl<>(List.of(t), pg, 1));
        when(detalleRepo.findByTrasladoId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("20.000"))));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
        when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));

        var result = service.list(pg);

        assertThat(result.getContent()).hasSize(1);
        TrasladoResponse resp = result.getContent().get(0);
        assertThat(resp.almacenOrigenNombre()).isEqualTo("Origen");
        assertThat(resp.almacenDestinoNombre()).isEqualTo("Destino");
        assertThat(resp.detalles()).hasSize(1);
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: retorna traslado con detalles")
    void getById_returnsTraslado() {
        Traslado t = sampleTraslado(1L, 1, 2);
        when(repo.findById(1L)).thenReturn(Optional.of(t));
        when(detalleRepo.findByTrasladoId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("10.000"))));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
        when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Clavo")));

        TrasladoResponse resp = service.getById(1L);

        assertThat(resp.trasladoId()).isEqualTo(1L);
        assertThat(resp.folio()).startsWith("TR-");
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Clavo");
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create ok: guarda traslado + detalles + 2 movimientos por detalle")
    void create_ok() {
        TrasladoRequest req = new TrasladoRequest(1, 2,
                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("10.000"))));

        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
        when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
        when(productoRepo.findById(1L)).thenReturn(Optional.of(sampleProducto(1L, "Tornillo")));

        Traslado savedTraslado = sampleTraslado(1L, 1, 2);
        when(repo.save(any(Traslado.class))).thenReturn(savedTraslado);
        when(detalleRepo.save(any(TrasladoDetalle.class)))
                .thenReturn(sampleDetalle(1L, 1L, new BigDecimal("10.000")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        when(movimientoRepo.save(any(MovimientoInventario.class)))
                .thenReturn(MovimientoInventario.builder().movimientoId(1L).build());
        when(detalleRepo.findByTrasladoId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("10.000"))));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));

        TrasladoResponse resp = service.create(req);

        assertThat(resp.trasladoId()).isEqualTo(1L);
        assertThat(resp.almacenOrigen()).isEqualTo(1);
        assertThat(resp.almacenDestino()).isEqualTo(2);
        verify(repo).save(any(Traslado.class));
        verify(movimientoRepo, times(2)).save(any(MovimientoInventario.class));
    }

    @Test
    @DisplayName("create mismo almacen origen y destino: ReglaNegocioException")
    void create_sameWarehouse() {
        TrasladoRequest req = new TrasladoRequest(1, 1,
                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("create producto inexistente: RecursoNoEncontradoException")
    void create_productNotFound() {
        TrasladoRequest req = new TrasladoRequest(1, 2,
                List.of(new TrasladoDetalleRequest(999L, new BigDecimal("5.000"))));

        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
        when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
        when(productoRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
