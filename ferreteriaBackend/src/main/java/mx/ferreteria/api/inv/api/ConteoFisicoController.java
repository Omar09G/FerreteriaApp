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
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoResponse;
import mx.ferreteria.api.inv.service.ConteoFisicoService;

@RestController
@RequestMapping("/api/v1/conteos-fisicos")
@RequiredArgsConstructor
@Validated
public class ConteoFisicoController {

    private final ConteoFisicoService service;

    @GetMapping
    public Page<ConteoFisicoResponse> list(
            @RequestParam(required = false) Integer almacenId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(almacenId, PageQuery.of(page, size, null).toPageable());
    }

    @GetMapping("/{id}")
    public ConteoFisicoResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConteoFisicoResponse create(@Valid @RequestBody ConteoFisicoRequest req) {
        return service.create(req);
    }
}
