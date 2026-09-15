package mx.ferreteria.api.inv.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam(name = "almacenId", required = false) Integer almacenId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "productoId", required = false) Long productoId,
            @RequestParam(name = "fechaInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.list(almacenId, estado, productoId, fechaInicio, fechaFin,
                PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public ConteoFisicoResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','ALMACENISTA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConteoFisicoResponse create(@Valid @RequestBody ConteoFisicoRequest req) {
        return service.create(req);
    }
}
