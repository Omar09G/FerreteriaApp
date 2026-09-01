package mx.ferreteria.api.fin.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.service.CajaService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/cajas")
@RequiredArgsConstructor
@Validated
public class CajaController {

    private final CajaService service;

    @GetMapping
    public List<FinDtos.CajaResponse> list() {
        return service.listCajas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinDtos.CajaResponse crearCaja(@Valid @RequestBody FinDtos.CajaRequest req) {
        return service.crearCaja(req);
    }

    @PutMapping("/{cajaId}")
    public FinDtos.CajaResponse actualizarCaja(
            @PathVariable Integer cajaId,
            @Valid @RequestBody FinDtos.CajaRequest req) {
        return service.actualizarCaja(cajaId, req);
    }

    @PutMapping("/estado/{cajaId}")
    public FinDtos.CajaResponse actualizarCajaEstado(
            @PathVariable Integer cajaId,
            @Valid @RequestBody FinDtos.CajaRequest req) {
        return service.actualizarCajaEstado(cajaId, req);
    }

    @GetMapping("/{cajaId}/turnos")
    public Page<FinDtos.TurnoCajaResponse> listTurnos(
            @PathVariable Integer cajaId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listTurnos(cajaId, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{cajaId}/turno-actual")
    public FinDtos.TurnoCajaResponse turnoActual(@PathVariable Integer cajaId) {
        return service.getTurnoActual(cajaId);
    }

    @PostMapping("/{cajaId}/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    public FinDtos.TurnoCajaResponse abrirTurno(
            @PathVariable Integer cajaId,
            @Valid @RequestBody FinDtos.TurnoAperturaRequest req) {
        var fullReq = new FinDtos.TurnoAperturaRequest(cajaId, req.montoApertura());
        return service.abrirTurno(fullReq);
    }

    @PostMapping("/{cajaId}/turnos/{turnoId}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public FinDtos.MovimientoCajaResponse registrarMovimiento(
            @PathVariable Long turnoId,
            @Valid @RequestBody FinDtos.MovimientoCajaRequest req) {
        return service.registrarMovimiento(turnoId, req);
    }

    @GetMapping("/{cajaId}/turnos/{turnoId}/movimientos")
    public List<FinDtos.MovimientoCajaResponse> listMovimientos(@PathVariable Long turnoId) {
        return service.listMovimientos(turnoId);
    }

    @GetMapping("/{cajaId}/turnos/{turnoId}/esperado")
    public FinDtos.EsperadoCajaResponse obtenerEsperado(@PathVariable Long turnoId) {
        return service.obtenerEsperado(turnoId);
    }

    @PostMapping("/{cajaId}/turnos/{turnoId}/corte")
    public FinDtos.CorteCajaResponse cerrarTurno(
            @PathVariable Long turnoId,
            @Valid @RequestBody FinDtos.CorteRequest req) {
        return service.cerrarTurno(turnoId, req);
    }
}
