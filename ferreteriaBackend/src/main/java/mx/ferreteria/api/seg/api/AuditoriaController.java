package mx.ferreteria.api.seg.api;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.AuditoriaResponse;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.TablaAuditoriaResponse;
import mx.ferreteria.api.seg.service.AuditoriaService;

/**
 * Listado de auditoría con filtros avanzados:
 * esquema, tabla, acción, usuario (ILIKE), registroId, rango de fechas y
 * texto libre en datos_anteriores/datos_nuevos (JSON). Solo lectura.
 * Acceso: ADMINISTRADOR o AUDITOR.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','AUDITOR')")
public class AuditoriaController {

    private final AuditoriaService service;

    @GetMapping
    public Page<AuditoriaResponse> listar(
            @RequestParam(required = false) String esquema,
            @RequestParam(required = false) String tabla,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) Long registroId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = PageQuery.of(page, size, sort).toPageable();
        return service.buscar(esquema, tabla, accion, usuario, registroId,
                fechaInicio, fechaFin, texto, pageable);
    }

    @GetMapping("/tablas")
    public List<TablaAuditoriaResponse> tablas() {
        return service.tablas();
    }
}