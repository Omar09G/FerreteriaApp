package mx.ferreteria.api.ven.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.CotizacionService;

@RestController
@RequestMapping("/api/v1/cotizaciones")
@RequiredArgsConstructor
@Validated
public class CotizacionController {

    private final CotizacionService service;

    @GetMapping
    public Page<VenDtos.CotizacionResponse> list(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(estado, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public VenDtos.CotizacionResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenDtos.CotizacionResponse create(@Valid @RequestBody VenDtos.CotizacionRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/convertir")
    public VenDtos.CotizacionResponse convertir(
            @PathVariable Long id,
            @RequestParam Integer almacenId,
            @RequestParam Integer formaPagoId) {
        return service.convertirAVenta(id, almacenId, formaPagoId);
    }
}
