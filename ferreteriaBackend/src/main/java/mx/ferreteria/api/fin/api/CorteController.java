package mx.ferreteria.api.fin.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.service.CajaService;

@RestController
@RequestMapping("/api/v1/cortes-caja")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class CorteController {

    private final CajaService service;

    @GetMapping
    public Page<FinDtos.CorteCajaResponse> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listCortes(desde, hasta, PageQuery.of(page, size, sort).toPageable());
    }
}
