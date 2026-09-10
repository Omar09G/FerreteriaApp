package mx.ferreteria.api.ven.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.CreditoService;

@RestController
@RequestMapping("/api/v1/creditos")
@RequiredArgsConstructor
@Validated
public class CreditoController {

    private final CreditoService service;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
    @GetMapping("/cobranza")
    public Page<VenDtos.CuentaCobrarResponse> listCuentas(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.listCuentas(desde, hasta, estado, PageQuery.of(page, size, sort).toPageable());
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')")
    @GetMapping("/{clienteId}")
    public Page<VenDtos.CuentaCobrarResponse> listByCliente(
            @PathVariable Long clienteId,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        return service.listCuentasByCliente(clienteId, desde, hasta, estado,
                PageQuery.of(page, size, sort).toPageable());
    }
}
