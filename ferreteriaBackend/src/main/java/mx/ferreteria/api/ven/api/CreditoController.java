package mx.ferreteria.api.ven.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/cobranza")
    public Page<VenDtos.CuentaCobrarResponse> listCuentas(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listCuentas(desde, hasta, estado, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{clienteId}")
    public Page<VenDtos.CuentaCobrarResponse> listByCliente(
            @PathVariable Long clienteId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listCuentasByCliente(clienteId, desde, hasta, estado,
                PageQuery.of(page, size, sort).toPageable());
    }
}
