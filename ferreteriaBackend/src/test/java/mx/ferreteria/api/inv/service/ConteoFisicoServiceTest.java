package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.ConteoFisico;
import mx.ferreteria.api.inv.entity.ConteoFisicoDetalle;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.entity.InventarioId;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoDetalleRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;
import mx.ferreteria.api.seg.service.SegAdminGateway;

@ExtendWith(MockitoExtension.class)
class ConteoFisicoServiceTest {

    @Mock
    ConteoFisicoRepository repo;

    @Mock
    ConteoFisicoDetalleRepository detalleRepo;

    @Mock
    InventarioRepository inventarioRepo;

    @Mock
    AlmacenRepository almacenRepo;

    @Mock
    ProductoRepository productoRepo;

    @Mock
    SegAdminGateway usuarios;

    @InjectMocks
    ConteoFisicoService service;

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder().almacenId(id).nombre(nombre).build();
    }

    private Producto sampleProducto(Long id, String codigo, String nombre) {
        return Producto.builder().productoId(id).codigo(codigo).nombre(nombre).build();
    }

    private ConteoFisico sampleConteo(Long id, Integer almacenId) {
        return ConteoFisico.builder()
                .conteoId(id)
                .almacenId(almacenId)
                .fecha(Instant.parse("2026-09-15T14:00:00Z"))
                .estado("EN_PROCESO")
                .usuarioId(1)
                .observaciones("Conteo mensual")
                .build();
    }

    private ConteoFisicoDetalle sampleDetalle(Long conteoId, Long productoId) {
        return ConteoFisicoDetalle.builder()
                .conteoId(conteoId).productoId(productoId)
                .cantidadSistema(new BigDecimal("50.000"))
                .cantidadFisica(new BigDecimal("48.000"))
                .build();
    }

    private void stubContexto(ConteoFisico c, ConteoFisicoDetalle d) {
        when(detalleRepo.findByConteoIdIn(List.of(c.getConteoId()))).thenReturn(List.of(d));
        when(productoRepo.findAllById(any())).thenReturn(List.of(sampleProducto(1L, "TORN-001", "Tornillo")));
        when(almacenRepo.findAllById(any())).thenReturn(List.of(sampleAlmacen(1, "Central")));
        when(usuarios.findUsuarioById(1)).thenReturn(Optional.of(
                new SegAdminGateway.UsuarioRow(1, "almacenista1", "a@x.mx", null, true, null, null)));
    }

    // ── list ────────────────────────────────────────────────────

    @Test
    @DisplayName("list: retorna pagina de conteos con detalles, producto y usuario")
    void list_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        ConteoFisico c = sampleConteo(1L, 1);
        ConteoFisicoDetalle d = sampleDetalle(1L, 1L);
        when(repo.filtrar(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(c), pg, 1));
        stubContexto(c, d);

        var result = service.list(1, null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        ConteoFisicoResponse resp = result.getContent().get(0);
        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.almacenNombre()).isEqualTo("Central");
        assertThat(resp.fecha()).isEqualTo(Instant.parse("2026-09-15T14:00:00Z"));
        assertThat(resp.usuarioNombre()).isEqualTo("almacenista1");
        assertThat(resp.totalPartidas()).isEqualTo(1);
        assertThat(resp.diferenciaTotal()).isEqualByComparingTo(new BigDecimal("-2.000"));
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoCodigo()).isEqualTo("TORN-001");
        assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Tornillo");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: retorna conteo con almacen y detalles resueltos")
    void getById_returnsConteo() {
        ConteoFisico c = sampleConteo(1L, 1);
        ConteoFisicoDetalle d = sampleDetalle(1L, 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        stubContexto(c, d);

        ConteoFisicoResponse resp = service.getById(1L);

        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.estado()).isEqualTo("EN_PROCESO");
        assertThat(resp.observaciones()).isEqualTo("Conteo mensual");
        assertThat(resp.totalPartidas()).isEqualTo(1);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create: guarda conteo con detalles y cantidadSistema desde inventario")
    void create_ok() {
        ConteoFisicoRequest req = new ConteoFisicoRequest(1, "Conteo trimestral",
                List.of(new ConteoFisicoDetalleRequest(1L, new BigDecimal("48.000"))));
        when(almacenRepo.existsById(1)).thenReturn(true);
        when(productoRepo.findAllById(any()))
                .thenReturn(List.of(sampleProducto(1L, "TORN-001", "Tornillo")));

        ConteoFisico savedConteo = sampleConteo(1L, 1);
        when(repo.save(any(ConteoFisico.class))).thenReturn(savedConteo);

        Inventario inv = Inventario.builder()
                .productoId(1L).almacenId(1)
                .stock(new BigDecimal("50.000"))
                .build();
        when(inventarioRepo.findById(new InventarioId(1L, 1)))
                .thenReturn(Optional.of(inv));
        when(detalleRepo.findByConteoIdIn(any())).thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(almacenRepo.findAllById(any())).thenReturn(List.of(sampleAlmacen(1, "Central")));
        when(usuarios.findUsuarioById(1)).thenReturn(Optional.of(
                new SegAdminGateway.UsuarioRow(1, "almacenista1", "a@x.mx", null, true, null, null)));

        ConteoFisicoResponse resp = service.create(req);

        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.almacenNombre()).isEqualTo("Central");
        assertThat(resp.totalPartidas()).isEqualTo(1);
        verify(repo).save(any(ConteoFisico.class));
        verify(detalleRepo).saveAll(anyList());
    }
}
