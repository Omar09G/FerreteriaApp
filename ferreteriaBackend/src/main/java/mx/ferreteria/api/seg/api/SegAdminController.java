package mx.ferreteria.api.seg.api;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.seg.dto.SegAdminDtos.OperacionOk;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisosRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisoRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisoResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolUpdateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioCreateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioPasswordRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioRolesRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioUpdateRequest;
import mx.ferreteria.api.seg.service.SegAdminService;

/**
 * Administración de seguridad (PLAN §6 seg): usuarios, roles, permisos.
 * Acceso EXCLUSIVO del rol ADMINISTRADOR; el alta pública para empleados
 * sin rol vive en POST /api/v1/auth/register (never admin).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class SegAdminController {

    private final SegAdminService service;

    @GetMapping("/usuarios")
    public Page<UsuarioResponse> listUsuarios(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listUsuarios(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/usuarios/{id}")
    public UsuarioResponse getUsuario(@PathVariable int id) {
        return service.getUsuario(id);
    }

    @PostMapping("/usuarios")
    public UsuarioResponse createUsuario(@Valid @RequestBody UsuarioCreateRequest req) {
        return service.createUsuario(req);
    }

    @PatchMapping("/usuarios/{id}")
    public UsuarioResponse updateUsuario(@PathVariable int id,
            @Valid @RequestBody UsuarioUpdateRequest req) {
        return service.updateUsuario(id, req);
    }

    @PatchMapping("/usuarios/{id}/password")
    public UsuarioResponse resetPassword(@PathVariable int id,
            @Valid @RequestBody UsuarioPasswordRequest req) {
        service.resetPassword(id, req);
        return service.getUsuario(id);
    }

    @PutMapping("/usuarios/{id}/roles")
    public UsuarioResponse setRoles(@PathVariable int id,
            @Valid @RequestBody UsuarioRolesRequest req) {
        return service.setRoles(id, req);
    }

    @DeleteMapping("/usuarios/{id}")
    public OperacionOk deleteUsuario(@PathVariable int id) {
        service.deleteUsuario(id);
        return new OperacionOk(true);
    }

    @GetMapping("/roles")
    public Page<RolResponse> listRoles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listRoles(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/roles/{id}")
    public RolResponse getRol(@PathVariable int id) {
        return service.getRol(id);
    }

    @PostMapping("/roles")
    public RolResponse createRol(@Valid @RequestBody RolRequest req) {
        return service.createRol(req);
    }

    @PatchMapping("/roles/{id}")
    public RolResponse updateRol(@PathVariable int id,
            @Valid @RequestBody RolUpdateRequest req) {
        return service.updateRol(id, req);
    }

    @DeleteMapping("/roles/{id}")
    public OperacionOk deleteRol(@PathVariable int id) {
        service.deleteRol(id);
        return new OperacionOk(true);
    }

    @GetMapping("/roles/{id}/permisos")
    public java.util.List<String> getPermisos(@PathVariable int id) {
        return service.getPermisosDe(id);
    }

    @PutMapping("/roles/{id}/permisos")
    public java.util.List<String> setPermisos(@PathVariable int id,
            @Valid @RequestBody PermisosRequest req) {
        return service.setPermisos(id, req);
    }

    @GetMapping("/permisos")
    public Page<PermisoResponse> listPermisos(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.listPermisos(PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/permisos/{id}")
    public PermisoResponse getPermiso(@PathVariable int id) {
        return service.getPermiso(id);
    }

    @PostMapping("/permisos")
    public PermisoResponse createPermiso(@Valid @RequestBody PermisoRequest req) {
        return service.createPermiso(req);
    }

    @PutMapping("/permisos/{id}")
    public PermisoResponse updatePermiso(@PathVariable int id,
            @Valid @RequestBody PermisoRequest req) {
        return service.updatePermiso(id, req);
    }

    @DeleteMapping("/permisos/{id}")
    public OperacionOk deletePermiso(@PathVariable int id) {
        service.deletePermiso(id);
        return new OperacionOk(true);
    }
}