package mx.ferreteria.api.fin.api;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.service.CajaService;

@RestController
@RequestMapping("/api/v1/cortes-caja")
@RequiredArgsConstructor
@Validated
public class CorteController {

    private final CajaService service;

    @GetMapping
    public Page<FinDtos.CorteCajaResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listCortes(PageQuery.of(page, size, sort).toPageable());
    }
}
