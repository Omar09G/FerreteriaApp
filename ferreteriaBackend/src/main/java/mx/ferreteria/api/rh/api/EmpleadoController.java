package mx.ferreteria.api.rh.api;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoCreateRequest;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoOk;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResponse;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoUpdateRequest;
import mx.ferreteria.api.rh.service.EmpleadoService;

/**
 * CRUD de empleados (PLAN §6): base del alta de usuarios. SOLO el rol
 * ADMINISTRADOR crea/edita empleados. POST /empleados con `username` (+rol)
 * crea además el usuario del sistema y le asigna los roles en la misma
 * transacción (orquestado por EmpleadoService vía UsuarioAltaGateway).
 */
@RestController
@RequestMapping("/api/v1/empleados")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class EmpleadoController {

    private final EmpleadoService service;

    @GetMapping
    public Page<EmpleadoResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public EmpleadoResponse get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping
    public EmpleadoResponse create(@Valid @RequestBody EmpleadoCreateRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    public EmpleadoResponse update(@PathVariable int id,
            @Valid @RequestBody EmpleadoUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public EmpleadoOk baja(@PathVariable int id) {
        service.baja(id);
        return new EmpleadoOk(true);
    }
}