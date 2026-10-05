package mx.ferreteria.api.inv.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final InventarioRepository repo;
    private final ProductoRepository productoRepo;
    private final AlmacenRepository almacenRepo;

    public Page<InventarioResponse> list(Pageable pageable) {
        Page<Inventario> page = repo.findAll(pageable);
        List<Long> productoIds = page.getContent().stream()
                .map(Inventario::getProductoId).distinct().toList();
        List<Integer> almacenIds = page.getContent().stream()
                .map(Inventario::getAlmacenId).distinct().toList();
        Map<Long, Producto> productos = productoRepo.findAllById(productoIds).stream()
                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                .collect(Collectors.toMap(Almacen::getAlmacenId, a -> a));
        return page.map(i -> toResponse(i, productos.get(i.getProductoId()),
                almacenes.get(i.getAlmacenId())));
    }

    public Page<InventarioResponse> listByAlmacen(Integer almacenId, Pageable pageable) {
        Page<Inventario> page = repo.findByAlmacenId(almacenId, pageable);
        List<Long> productoIds = page.getContent().stream()
                .map(Inventario::getProductoId).distinct().toList();
        Map<Long, Producto> productos = productoRepo.findAllById(productoIds).stream()
                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        return page.map(i -> toResponse(i, productos.get(i.getProductoId()), null));
    }

    public Page<InventarioResponse> listBajoStock(Pageable pageable) {
        Page<Inventario> page = repo.findBajoStock(pageable);
        List<InventarioResponse> mapped = completar(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(mapped,
                page.getPageable(), page.getTotalElements());
    }

    /**
     * Bajo stock completo (recordatorio diario + Excel adjunto): sin paginar.
     */
    public List<InventarioResponse> bajoStockCompleto() {
        return completar(repo.findTodoBajoStock());
    }

    private List<InventarioResponse> completar(List<Inventario> filas) {
        List<Long> productoIds = filas.stream()
                .map(Inventario::getProductoId).distinct().toList();
        List<Integer> almacenIds = filas.stream()
                .map(Inventario::getAlmacenId).distinct().toList();
        Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                : productoRepo.findAllById(productoIds).stream()
                        .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        Map<Integer, Almacen> almacenes = almacenIds.isEmpty() ? Map.of()
                : almacenRepo.findAllById(almacenIds).stream()
                        .collect(Collectors.toMap(Almacen::getAlmacenId, a -> a));
        return filas.stream().map(i -> toResponse(i, productos.get(i.getProductoId()),
                almacenes.get(i.getAlmacenId()))).toList();
    }

    public List<InventarioResponse> getStockByProducto(Long productoId) {
        List<Inventario> inventarios = repo.findByProductoId(productoId);
        List<Integer> almacenIds = inventarios.stream()
                .map(Inventario::getAlmacenId).distinct().toList();
        Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                .collect(Collectors.toMap(Almacen::getAlmacenId, a -> a));
        Producto producto = productoRepo.findById(productoId).orElse(null);
        return inventarios.stream()
                .map(i -> toResponse(i, producto, almacenes.get(i.getAlmacenId())))
                .toList();
    }

    private InventarioResponse toResponse(Inventario i, Producto p, Almacen a) {
        return new InventarioResponse(
                i.getProductoId(),
                p != null ? p.getNombre() : null,
                p != null ? p.getCodigo() : null,
                i.getAlmacenId(),
                a != null ? a.getNombre() : null,
                i.getStock(),
                i.getStockMinimo(),
                i.getStockMaximo(),
                i.getReservado());
    }
}
