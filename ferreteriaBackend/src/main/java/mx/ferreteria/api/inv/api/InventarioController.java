package mx.ferreteria.api.inv.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;
import mx.ferreteria.api.inv.service.InventarioService;

@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@Validated
public class InventarioController {

    private final InventarioService service;

    @GetMapping
    public Page<InventarioResponse> list(
            @RequestParam(required = false) Integer almacenId,
            @RequestParam(required = false) Boolean soloBajoStock,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var pageable = PageQuery.of(page, size, null).toPageable();
        if (Boolean.TRUE.equals(soloBajoStock)) {
            if (almacenId != null) {
                return service.listByAlmacen(almacenId, pageable);
            }
            return service.listBajoStock(pageable);
        }
        if (almacenId != null) {
            return service.listByAlmacen(almacenId, pageable);
        }
        return service.list(pageable);
    }

    @GetMapping("/producto/{productoId}")
    public List<InventarioResponse> getStockByProducto(@PathVariable Long productoId) {
        return service.getStockByProducto(productoId);
    }
}
