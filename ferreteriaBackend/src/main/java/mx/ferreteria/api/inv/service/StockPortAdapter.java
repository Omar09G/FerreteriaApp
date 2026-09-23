package mx.ferreteria.api.inv.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.service.StockPort;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.repo.InventarioRepository;

/**
 * Adapter de {@link StockPort} (contrato de {@code cat}) sobre el
 * repositorio de {@code inv}. La arista inv→cat ya existe y es
 * unidireccional, así que no forma ciclo. {@code @Component} (no
 * {@code @Service}) por la convención de naming del proyecto.
 */
@Component
@RequiredArgsConstructor
public class StockPortAdapter implements StockPort {

    private final InventarioRepository inventarioRepo;

    @Override
    public Map<Long, BigDecimal> stockPorProductos(Integer almacenId, List<Long> productoIds) {
        if (almacenId == null || productoIds == null || productoIds.isEmpty()) {
            return Map.of();
        }
        return inventarioRepo.findByAlmacenIdAndProductoIdIn(almacenId, productoIds).stream()
                .collect(Collectors.toMap(Inventario::getProductoId,
                        i -> i.getStock() != null ? i.getStock() : BigDecimal.ZERO,
                        (a, b) -> a));
    }

    @Override
    public BigDecimal stockDeProducto(Integer almacenId, Long productoId) {
        if (almacenId == null || productoId == null) {
            return BigDecimal.ZERO;
        }
        Inventario inv = inventarioRepo.findByAlmacenIdAndProductoId(almacenId, productoId);
        return inv != null && inv.getStock() != null ? inv.getStock() : BigDecimal.ZERO;
    }
}
