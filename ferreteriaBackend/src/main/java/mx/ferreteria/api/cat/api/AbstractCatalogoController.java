package mx.ferreteria.api.cat.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.common.web.RateLimited;

/**
 * Controller base genérico para CRUD de catálogos. Cada controller concreto
 * define su ruta raíz con {@code @RequestMapping} y hereda los endpoints.
 */
@Validated
@RateLimited("catalogo")
public abstract class AbstractCatalogoController<T, ID, REQ, RES> {

    protected abstract AbstractCatalogoService<T, ID, REQ, RES> service();

    @GetMapping
    public Page<RES> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service().findAll(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public RES getById(@PathVariable ID id) {
        return service().findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RES create(@Valid @RequestBody REQ req) {
        return service().create(req);
    }

    @PutMapping("/{id}")
    public RES update(@PathVariable ID id, @Valid @RequestBody REQ req) {
        return service().update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable ID id) {
        service().deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
