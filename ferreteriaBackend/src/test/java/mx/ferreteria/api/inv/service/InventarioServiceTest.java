package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock
    InventarioRepository repo;

    @Mock
    ProductoRepository productoRepo;

    @Mock
    AlmacenRepository almacenRepo;

    @InjectMocks
    InventarioService service;

    private Producto sampleProducto(Long id, String nombre, String codigo) {
        return Producto.builder().productoId(id).codigo(codigo).nombre(nombre).build();
    }

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder().almacenId(id).nombre(nombre).build();
    }

    private Inventario sampleInventario(Long productoId, Integer almacenId, String stock) {
        return Inventario.builder()
                .productoId(productoId)
                .almacenId(almacenId)
                .stock(new BigDecimal(stock))
                .stockMinimo(new BigDecimal("5.000"))
                .stockMaximo(new BigDecimal("100.000"))
                .reservado(BigDecimal.ZERO)
                .build();
    }

    // ── listByAlmacen ───────────────────────────────────────────────

    @Test
    @DisplayName("listByAlmacen: retorna pagina con nombres de producto resueltos")
    void listByAlmacen_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Inventario inv = sampleInventario(1L, 1, "50.000");
        when(repo.findByAlmacenId(1, pg))
                .thenReturn(new PageImpl<>(List.of(inv), pg, 1));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Tornillo", "P001")));

        var result = service.listByAlmacen(1, pg);

        assertThat(result.getContent()).hasSize(1);
        InventarioResponse resp = result.getContent().get(0);
        assertThat(resp.productoNombre()).isEqualTo("Tornillo");
        assertThat(resp.productoCodigo()).isEqualTo("P001");
        assertThat(resp.stock()).isEqualByComparingTo(new BigDecimal("50.000"));
    }

    // ── listBajoStock ───────────────────────────────────────────────

    @Test
    @DisplayName("listBajoStock: retorna items con stock bajo minimo")
    void listBajoStock_returnsLowStockItems() {
        Pageable pg = PageRequest.of(0, 10);
        Inventario inv = sampleInventario(1L, 1, "3.000");
        when(repo.findBajoStock(pg))
                .thenReturn(new PageImpl<>(List.of(inv), pg, 1));
        when(productoRepo.findAllById(List.of(1L)))
                .thenReturn(List.of(sampleProducto(1L, "Clavo", "P002")));
        when(almacenRepo.findAllById(List.of(1)))
                .thenReturn(List.of(sampleAlmacen(1, "Central")));

        var result = service.listBajoStock(pg);

        assertThat(result.getContent()).hasSize(1);
        InventarioResponse resp = result.getContent().get(0);
        assertThat(resp.stock()).isEqualByComparingTo(new BigDecimal("3.000"));
        assertThat(resp.almacenNombre()).isEqualTo("Central");
    }

    // ── getStockByProducto ──────────────────────────────────────────

    @Test
    @DisplayName("getStockByProducto: retorna stock en todos los almacenes")
    void getStockByProducto_returnsStockAcrossWarehouses() {
        Inventario inv1 = sampleInventario(1L, 1, "30.000");
        Inventario inv2 = sampleInventario(1L, 2, "15.000");
        when(repo.findByProductoId(1L)).thenReturn(List.of(inv1, inv2));
        when(almacenRepo.findAllById(List.of(1, 2)))
                .thenReturn(List.of(sampleAlmacen(1, "Central"), sampleAlmacen(2, "Norte")));
        when(productoRepo.findById(1L))
                .thenReturn(Optional.of(sampleProducto(1L, "Tornillo", "P001")));

        List<InventarioResponse> result = service.getStockByProducto(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).almacenNombre()).isEqualTo("Central");
        assertThat(result.get(1).almacenNombre()).isEqualTo("Norte");
    }
}
