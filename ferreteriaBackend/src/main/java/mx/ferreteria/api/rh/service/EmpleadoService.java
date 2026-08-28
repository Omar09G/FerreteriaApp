package mx.ferreteria.api.rh.service;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoCreateRequest;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResponse;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoUpdateRequest;
import mx.ferreteria.api.rh.service.EmpleadoGateway.EmpleadoRow;

/**
 * CRUD de empleados (PLAN §6 seg /empleados). Exclusivo de ADMINISTRADOR
 * (guard vía @PreAuthorize). rh.empleados es la BASE de seg.usuarios; una
 * baja no borra físicamente (referenciado por usuarios y nómina). POST create
 * con `username` (+password) crea ADEMÁS el usuario del sistema ligado al
 * empleado y le asigna los roles (puerto UsuarioAltaGateway implementado en
 * seg, una sola transacción).
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoGateway gateway;
    private final UsuarioAltaGateway usuarioAlta;

    public Page<EmpleadoResponse> list(Pageable pageable) {
        List<EmpleadoResponse> content = gateway.findEmpleados(
                        pageable.getPageSize(), Math.toIntExact(pageable.getOffset()))
                .stream().map(this::toResponse)
                .toList();
        return new PageImpl<>(content, pageable, gateway.countEmpleados());
    }

    public EmpleadoResponse get(int empleadoId) {
        return toResponse(exigir(empleadoId));
    }

    @Transactional
    public EmpleadoResponse create(EmpleadoCreateRequest req) {
        if (req.conUsuario() && (req.password() == null || req.password().isBlank()
                || req.password().length() < 8)) {
            throw new ValidacionException(ErrorCode.CAMPO_REQUERIDO);
        }
        int id = gateway.create(req.puestoId(), req.nombre(), req.apellidoPaterno(),
                req.apellidoMaterno(), req.curp(), req.nss(), req.telefono(), req.email(),
                req.calle(), req.colonia(), req.ciudadId(), req.cp(),
                req.fechaIngreso() == null ? java.time.LocalDate.now() : req.fechaIngreso(),
                req.sueldoDiario() == null ? java.math.BigDecimal.ZERO : req.sueldoDiario());
        if (req.conUsuario()) {
            // email coherente usuario↔empleado: el empleado usa req.email (o null)
            usuarioAlta.crearUsuarioConRoles(req.username(), req.email(), req.password(), id,
                    req.roles() == null ? List.of() : req.roles());
        }
        return get(id);
    }

    @Transactional
    public EmpleadoResponse update(int empleadoId, EmpleadoUpdateRequest req) {
        exigir(empleadoId);
        gateway.update(empleadoId, req.puestoId(), req.nombre(), req.apellidoPaterno(),
                req.apellidoMaterno(), req.curp(), req.nss(), req.telefono(), req.email(),
                req.calle(), req.colonia(), req.ciudadId(), req.cp(), req.fechaIngreso(),
                req.sueldoDiario(), req.activo());
        return get(empleadoId);
    }

    public void baja(int empleadoId) {
        exigir(empleadoId);
        gateway.baja(empleadoId);
    }

    private EmpleadoRow exigir(int empleadoId) {
        return gateway.findById(empleadoId)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private EmpleadoResponse toResponse(EmpleadoRow r) {
        return new EmpleadoResponse(r.empleadoId(), r.puestoId(), r.puestoNombre(),
                r.nombre(), r.apellidoPaterno(), r.apellidoMaterno(), r.curp(), r.nss(),
                r.telefono(), r.email(), r.calle(), r.colonia(), r.ciudadId(), r.cp(),
                r.fechaIngreso(), r.fechaBaja(), r.sueldoDiario(), r.activo());
    }
}