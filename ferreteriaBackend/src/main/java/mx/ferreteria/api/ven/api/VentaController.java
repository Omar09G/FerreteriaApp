package mx.ferreteria.api.ven.api;

import java.time.Instant;


import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.VentaService;

@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
@Validated
public class VentaController {

    private final VentaService service;

    @GetMapping
    public Page<VenDtos.VentaResponse> list(
            @RequestParam(required = false) Integer almacenId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(almacenId, desde, hasta, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public VenDtos.VentaResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenDtos.VentaResponse checkout(@Valid @RequestBody VenDtos.VentaRequest req) {
        return service.checkout(req);
    }

    @PatchMapping("/{id}/cancelar")
    public VenDtos.VentaResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody VenDtos.VentaCancelRequest req) {
        return service.cancel(id, req.motivo());
    }
}
