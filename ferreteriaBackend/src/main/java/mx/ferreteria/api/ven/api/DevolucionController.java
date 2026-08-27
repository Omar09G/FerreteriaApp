package mx.ferreteria.api.ven.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.DevolucionService;

@RestController
@RequestMapping("/api/v1/devoluciones")
@RequiredArgsConstructor
@Validated
public class DevolucionController {

    private final DevolucionService service;

    @GetMapping("/venta/{ventaId}")
    public Page<VenDtos.DevolucionResponse> listByVenta(
            @PathVariable Long ventaId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listByVenta(ventaId, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public VenDtos.DevolucionResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenDtos.DevolucionResponse create(@Valid @RequestBody VenDtos.DevolucionRequest req) {
        return service.create(req);
    }
}
