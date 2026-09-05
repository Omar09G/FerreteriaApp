package mx.ferreteria.api.cat.api;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import mx.ferreteria.api.cat.dto.CatDtos.ProductoRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoResponse;
import mx.ferreteria.api.cat.service.ProductoService;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.common.web.RateLimited;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Validated
@RateLimited("catalogo")
public class ProductoController {

    private final ProductoService service;

    @GetMapping
    public Page<ProductoResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) Integer marcaId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer almacenId) {
        return service.list(q, categoriaId, marcaId, tipo, almacenId, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public ProductoResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse create(@Valid @RequestBody ProductoRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ProductoResponse update(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
