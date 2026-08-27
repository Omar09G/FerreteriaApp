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

import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaResponse;
import mx.ferreteria.api.cat.service.UnidadMedidaService;
import mx.ferreteria.api.common.web.PageQuery;

@RestController
@RequestMapping("/api/v1/unidades-medida")
@RequiredArgsConstructor
@Validated
public class UnidadMedidaController {

    private final UnidadMedidaService service;

    @GetMapping
    public Page<UnidadMedidaResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(q, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public UnidadMedidaResponse getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadMedidaResponse create(@Valid @RequestBody UnidadMedidaRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public UnidadMedidaResponse update(@PathVariable Integer id, @Valid @RequestBody UnidadMedidaRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Integer id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
