package mx.ferreteria.api.fin.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.service.GastoService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class GastoController {

    private final GastoService service;

    @GetMapping("/gastos")
    public Page<FinDtos.GastoResponse> listGastos(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listGastos(PageQuery.of(page, size, sort).toPageable());
    }

    @PostMapping("/gastos")
    @ResponseStatus(HttpStatus.CREATED)
    public FinDtos.GastoResponse createGasto(@Valid @RequestBody FinDtos.GastoRequest req) {
        return service.createGasto(req);
    }

    @GetMapping("/ingresos-otros")
    public Page<FinDtos.IngresoOtroResponse> listIngresos(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listIngresos(PageQuery.of(page, size, sort).toPageable());
    }

    @PostMapping("/ingresos-otros")
    @ResponseStatus(HttpStatus.CREATED)
    public FinDtos.IngresoOtroResponse createIngreso(@Valid @RequestBody FinDtos.IngresoOtroRequest req) {
        return service.createIngreso(req);
    }
}
