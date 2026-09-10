package mx.ferreteria.api.com.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam(name = "almacenId", required = false) Integer almacenId,
            @RequestParam(name = "proveedorId", required = false) Integer proveedorId,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.list(almacenId, proveedorId, desde, hasta,
                PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public ComDtos.CompraResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','ALMACENISTA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComDtos.CompraResponse create(@Valid @RequestBody ComDtos.CompraRequest req) {
        return service.create(req);
    }
}