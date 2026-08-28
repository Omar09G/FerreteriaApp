package mx.ferreteria.api.cat.api;

import java.util.List;

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

import mx.ferreteria.api.cat.dto.CatDtos.CategoriaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.CategoriaResponse;
import mx.ferreteria.api.cat.service.CategoriaService;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.common.web.RateLimited;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
@Validated
@RateLimited("catalogo")
public class CategoriaController {

    private final CategoriaService service;

    @GetMapping
    public Page<CategoriaResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(q, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/arbol")
    public List<CategoriaResponse> listTree() {
        return service.listTree();
    }

    @GetMapping("/{id}")
    public CategoriaResponse getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse create(@Valid @RequestBody CategoriaRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public CategoriaResponse update(@PathVariable Integer id, @Valid @RequestBody CategoriaRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Integer id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
