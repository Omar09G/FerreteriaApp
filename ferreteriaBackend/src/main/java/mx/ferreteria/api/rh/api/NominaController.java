package mx.ferreteria.api.rh.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.rh.dto.RhDtos;
import mx.ferreteria.api.rh.service.NominaService;

@RestController
@RequestMapping("/api/v1/nomina")
@RequiredArgsConstructor
@Validated
public class NominaController {

    private final NominaService service;

    @GetMapping
    public Page<RhDtos.NominaResponse> list(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(estado, desde, hasta, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public RhDtos.NominaResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RhDtos.NominaResponse create(@Valid @RequestBody RhDtos.NominaRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/pagar")
    public RhDtos.NominaResponse pagar(@PathVariable Long id) {
        return service.marcarPagada(id);
    }

    @PostMapping("/{id}/cancelar")
    public RhDtos.NominaResponse cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }
}