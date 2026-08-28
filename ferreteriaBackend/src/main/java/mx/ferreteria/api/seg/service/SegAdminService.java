package mx.ferreteria.api.seg.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.rh.service.UsuarioAltaGateway;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisosRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisoResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolUpdateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioCreateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioPasswordRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioResponse;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioRolesRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioUpdateRequest;
import mx.ferreteria.api.seg.service.SegAdminGateway.RolRow;
import mx.ferreteria.api.seg.service.SegAdminGateway.UsuarioRow;

/**
 * CRUD de seguridad (PLAN §6 seg). Endpoints exclusivos de ADMINISTRADOR
 * (guardado con @PreAuthorize en el controller). Reglas de arranque:
 *  - PASSWORD_NUNCA en respuestas; solo hash BCrypt al guardar.
 *  - Los CLAVES de rol/permiso se validan contra el catálogo activo; clave
 *    inexistente -> 400 REFERENCIA_INVALIDA (rollback total).
 *  - Asignación de CLAVES sin permiso sobre la referencia es imposible:
 *    reemplazo atómico (DELETE+INSERT) dentro de una transacción.
 */
@Service
@RequiredArgsConstructor
public class SegAdminService implements UsuarioAltaGateway {

    private final SegAdminGateway gateway;
    private final AuthUserGateway auth;
    private final EmpleadoGateway empleados;
    private final PasswordEncoder passwordEncoder;

    /**
     * Puerto consumido por rh (POST /empleados con username): crea usuario
     * ligado al empleado + roles reemplazados y validados; BCrypt al guardar.
     */
    @Override
    @Transactional
    public int crearUsuarioConRoles(String username, String email, String password,
                                    int empleadoId, List<String> roles) {
        int usuarioId = gateway.createUsuario(username, email,
                passwordEncoder.encode(password), empleadoId, true);
        guardarRoles(usuarioId, roles);
        return usuarioId;
    }

    public Page<UsuarioResponse> listUsuarios(Pageable pageable) {
        List<UsuarioResponse> content = gateway.findUsuarios(
                        pageable.getPageSize(), Math.toIntExact(pageable.getOffset()))
                .stream().map(this::toUsuario)
                .toList();
        return new PageImpl<>(content, pageable, gateway.countUsuarios());
    }

    public UsuarioResponse getUsuario(int usuarioId) {
        return toUsuario(exigirUsuario(usuarioId));
    }

    @Transactional
    public UsuarioResponse createUsuario(UsuarioCreateRequest req) {
        String email = validarVinculoEmpleado(req.empleadoId(), req.email());
        UsuarioRow creado = gateway.findUsuarioById(gateway.createUsuario(
                req.username(), email, passwordEncoder.encode(req.password()),
                req.empleadoId(), true))
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.ERROR_INTERNO));
        guardarRoles(creado.usuarioId(), req.roles());
        return toUsuario(gateway.findUsuarioById(creado.usuarioId()).orElse(creado));
    }

    @Transactional
    public UsuarioResponse updateUsuario(int usuarioId, UsuarioUpdateRequest req) {
        exigirUsuario(usuarioId);
        validarVinculoEmpleado(req.empleadoId(), req.email());
        gateway.updateUsuarioBasico(usuarioId, req.username(), req.email(), req.empleadoId(), req.activo());
        return getUsuario(usuarioId);
    }

    @Transactional
    public void resetPassword(int usuarioId, UsuarioPasswordRequest req) {
        exigirUsuario(usuarioId);
        gateway.actualizarPassword(usuarioId, passwordEncoder.encode(req.nuevaPassword()));
    }

    @Transactional
    public UsuarioResponse setRoles(int usuarioId, UsuarioRolesRequest req) {
        exigirUsuario(usuarioId);
        guardarRoles(usuarioId, req.roles());
        return getUsuario(usuarioId);
    }

    @Transactional
    public void deleteUsuario(int usuarioId) {
        exigirUsuario(usuarioId);
        gateway.borrarUsuario(usuarioId);
    }

    public Page<RolResponse> listRoles(Pageable pageable) {
        List<RolResponse> content = gateway.findRoles(
                        pageable.getPageSize(), Math.toIntExact(pageable.getOffset()))
                .stream().map(this::toRol)
                .toList();
        return new PageImpl<>(content, pageable, gateway.countRoles());
    }

    public RolResponse getRol(int rolId) {
        return toRol(exigirRol(rolId));
    }

    public RolResponse createRol(RolRequest req) {
        int rolId = gateway.createRol(req.clave(), req.nombre(), req.descripcion(),
                req.activo() == null || req.activo());
        return getRol(rolId);
    }

    @Transactional
    public RolResponse updateRol(int rolId, RolUpdateRequest req) {
        exigirRol(rolId);
        gateway.updateRol(rolId, req.nombre(), req.descripcion(), req.activo());
        return getRol(rolId);
    }

    public void deleteRol(int rolId) {
        exigirRol(rolId);
        gateway.desactivarRol(rolId);
    }

    public List<String> getPermisosDe(int rolId) {
        exigirRol(rolId);
        return gateway.permisosDe(rolId);
    }

    @Transactional
    public List<String> setPermisos(int rolId, PermisosRequest req) {
        exigirRol(rolId);
        Set<String> claves = req.permisos() == null ? Set.of() : new LinkedHashSet<>(req.permisos());
        exigirClaves(gateway.permisoClaves(), claves);
        gateway.reemplazarPermisos(rolId, claves);
        return gateway.permisosDe(rolId);
    }

    public Page<PermisoResponse> listPermisos(Pageable pageable) {
        List<PermisoResponse> content = gateway.findPermisos(
                        pageable.getPageSize(), Math.toIntExact(pageable.getOffset()))
                .stream().map(this::toPermiso)
                .toList();
        return new PageImpl<>(content, pageable, gateway.countPermisos());
    }

    public PermisoResponse getPermiso(int permisoId) {
        return gateway.findPermisoById(permisoId)
                .map(this::toPermiso)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private UsuarioRow exigirUsuario(int usuarioId) {
        return gateway.findUsuarioById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private RolRow exigirRol(int rolId) {
        return gateway.findRolById(rolId)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private void guardarRoles(int usuarioId, List<String> claves) {
        Set<String> solicitadas = claves == null ? Set.of() : new LinkedHashSet<>(claves);
        exigirClaves(gateway.rolClavesActivas(), solicitadas);
        gateway.reemplazarRoles(usuarioId, solicitadas);
    }

    /**
     * Vínculo usuario↔empleado (campos similares): el empleado debe existir y
     * estar activo; si ambos traen email, deben coincidir; si el usuario omite
     * email, se toma el del empleado.
     */
    private String validarVinculoEmpleado(Integer empleadoId, String email) {
        if (empleadoId == null) {
            return email;
        }
        EmpleadoResumen emp = empleados.resumenById(empleadoId)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.REFERENCIA_INVALIDA));
        if (!emp.activo()) {
            throw new ReglaNegocioException(ErrorCode.REFERENCIA_INVALIDA);
        }
        if (email != null && emp.email() != null
                && !email.equalsIgnoreCase(emp.email())) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        return email != null ? email : emp.email();
    }

    private static void exigirClaves(Set<String> existentes, Set<String> solicitadas) {
        if (!existentes.containsAll(solicitadas)) {
            throw new ReglaNegocioException(ErrorCode.REFERENCIA_INVALIDA);
        }
    }

    private UsuarioResponse toUsuario(UsuarioRow r) {
    return new UsuarioResponse(r.usuarioId(), r.username(), r.email(), r.empleadoId(),
            r.activo(), auth.rolesOf(r.usuarioId()),
            resumenEmpleado(r.empleadoId()), r.ultimoLogin(), r.creadoEn());
}

private EmpleadoResumen resumenEmpleado(Integer empleadoId) {
    return empleadoId == null ? null : empleados.resumenById(empleadoId).orElse(null);
}

    private RolResponse toRol(RolRow r) {
        return new RolResponse(r.rolId(), r.clave(), r.nombre(), r.descripcion(), r.activo(),
                gateway.permisosDe(r.rolId()));
    }

    private PermisoResponse toPermiso(SegAdminGateway.PermisoRow p) {
        return new PermisoResponse(p.permisoId(), p.clave(), p.descripcion());
    }
}