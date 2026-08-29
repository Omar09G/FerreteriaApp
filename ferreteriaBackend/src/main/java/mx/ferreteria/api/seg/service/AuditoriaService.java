package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.AuditoriaResponse;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.TablaAuditoriaResponse;
import mx.ferreteria.api.seg.service.AuditoriaGateway.AuditoriaRow;
import mx.ferreteria.api.seg.service.AuditoriaGateway.TablaRow;

/**
 * Listado y filtros de auditoría. Acceso EXCLUSIVO de ADMINISTRADOR o
 * AUDITOR (gate por rol en el controller). Orden por id descendente =
 * inserciones más recientes primero.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaGateway gateway;

    public Page<AuditoriaResponse> buscar(String esquema, String tabla, String accion, String usuario,
                                          Long registroId, LocalDate fechaInicio, LocalDate fechaFin,
                                          String texto, Pageable pageable) {
        validarRango(fechaInicio, fechaFin);
        Instant desde = fechaInicio == null ? null : fechaInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hasta = fechaFin   == null ? null : fechaFin.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        AuditoriaGateway.Filtro f = new AuditoriaGateway.Filtro(
                esquema, tabla, accion, usuario, registroId,
                desde, hasta, texto,
                pageable.getPageSize(), Math.toIntExact(pageable.getOffset()));

        List<AuditoriaRow> rows = gateway.buscar(f);
        long total = gateway.contar(f);
        List<AuditoriaResponse> content = rows.stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, total);
    }

    public List<TablaAuditoriaResponse> tablas() {
        return gateway.tablas().stream()
                .map(t -> new TablaAuditoriaResponse(t.esquema(), t.tabla()))
                .toList();
    }

    private static void validarRango(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
    }

    private AuditoriaResponse toResponse(AuditoriaRow r) {
        return new AuditoriaResponse(
                r.auditoriaId(), r.esquema(), r.tabla(), r.registroId(), r.accion(),
                r.datosAnteriores(), r.datosNuevos(), r.usuarioId(), r.usuario(), r.creadoEn());
    }
}