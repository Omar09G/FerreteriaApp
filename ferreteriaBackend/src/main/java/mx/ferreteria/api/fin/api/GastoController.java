package mx.ferreteria.api.fin.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PutMapping("/gastos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FinDtos.GastoResponse updateGasto(@PathVariable Long id,
            @Valid @RequestBody FinDtos.GastoRequest req) {
        return service.updateGasto(id, req);
    }

    @DeleteMapping("/gastos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void deleteGasto(@PathVariable Long id) {
        service.deleteGasto(id);
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

    @PutMapping("/ingresos-otros/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FinDtos.IngresoOtroResponse updateIngreso(@PathVariable Long id,
            @Valid @RequestBody FinDtos.IngresoOtroRequest req) {
        return service.updateIngreso(id, req);
    }

    @DeleteMapping("/ingresos-otros/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void deleteIngreso(@PathVariable Long id) {
        service.deleteIngreso(id);
    }
}
