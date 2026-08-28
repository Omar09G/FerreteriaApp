package mx.ferreteria.api.com.api;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.com.dto.ComDtos;
import mx.ferreteria.api.com.service.CompraService;
import mx.ferreteria.api.common.web.PageQuery;

@RestController
@RequestMapping("/api/v1/compras")
@RequiredArgsConstructor
@Validated
public class CompraController {

    private final CompraService service;

    @GetMapping
    public Page<ComDtos.CompraResponse> list(
            @RequestParam(required = false) Integer almacenId,
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(almacenId, proveedorId, desde, hasta,
                PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public ComDtos.CompraResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComDtos.CompraResponse create(@Valid @RequestBody ComDtos.CompraRequest req) {
        return service.create(req);
    }
}