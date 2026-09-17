package mx.ferreteria.api.rh.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.rh.dto.RhDtos;
import mx.ferreteria.api.rh.service.NominaService;

@RestController
@RequestMapping("/api/v1/nomina")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
@RequiredArgsConstructor
@Validated
public class NominaController {

    private final NominaService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR','ENCARGADO_CAJA','ALMACENISTA','AUDITOR')")
    public Page<RhDtos.NominaResponse> list(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.list(estado, desde, hasta, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR','ENCARGADO_CAJA','ALMACENISTA','AUDITOR')")
    public RhDtos.NominaResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RhDtos.NominaResponse create(@Valid @RequestBody RhDtos.NominaRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/pagar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public RhDtos.NominaResponse pagar(@PathVariable Long id) {
        return service.marcarPagada(id);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public RhDtos.NominaResponse cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @PostMapping("/generar-quincena")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public RhDtos.GenerarQuincenaResponse generarQuincena(@Valid @RequestBody RhDtos.GenerarQuincenaRequest req) {
        return service.generarQuincena(req);
    }

    @PostMapping("/pagar-lote")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public RhDtos.PagarLoteResponse pagarLote(@Valid @RequestBody RhDtos.PagarLoteRequest req) {
        return service.pagarLote(req);
    }
}