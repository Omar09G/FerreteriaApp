package mx.ferreteria.api.inv.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioRequest;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioResponse;
import mx.ferreteria.api.inv.service.MovimientoService;

@RestController
@RequestMapping("/api/v1/movimientos")
@RequiredArgsConstructor
@Validated
public class MovimientoController {

    private final MovimientoService service;

    @GetMapping
    public Page<MovimientoInventarioResponse> list(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Integer almacenId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageQuery.of(page, size, sort).toPageable();
        if (productoId != null) {
            return service.listByProducto(productoId, pageable);
        }
        if (almacenId != null) {
            return service.listByAlmacen(almacenId, pageable);
        }
        return service.list(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventarioResponse create(@Valid @RequestBody MovimientoInventarioRequest req) {
        return service.create(req);
    }
}
