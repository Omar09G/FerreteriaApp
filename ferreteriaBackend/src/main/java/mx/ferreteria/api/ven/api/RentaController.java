package mx.ferreteria.api.ven.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.RentaService;

@RestController
@RequestMapping("/api/v1/rentas")
@RequiredArgsConstructor
@Validated
public class RentaController {

    private final RentaService service;

    @GetMapping
    public Page<VenDtos.RentaResponse> list(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(estado, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public VenDtos.RentaResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenDtos.RentaResponse create(@Valid @RequestBody VenDtos.RentaRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/devolucion")
    public VenDtos.RentaResponse devolver(
            @PathVariable Long id,
            @Valid @RequestBody VenDtos.RentaDevolucionRequest req) {
        return service.devolver(id, req);
    }
}
