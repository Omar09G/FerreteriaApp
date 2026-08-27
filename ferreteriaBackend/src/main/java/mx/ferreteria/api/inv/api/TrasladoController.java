package mx.ferreteria.api.inv.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoResponse;
import mx.ferreteria.api.inv.service.TrasladoService;

@RestController
@RequestMapping("/api/v1/traslados")
@RequiredArgsConstructor
@Validated
public class TrasladoController {

    private final TrasladoService service;

    @GetMapping
    public Page<TrasladoResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public TrasladoResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrasladoResponse create(@Valid @RequestBody TrasladoRequest req) {
        return service.create(req);
    }
}
