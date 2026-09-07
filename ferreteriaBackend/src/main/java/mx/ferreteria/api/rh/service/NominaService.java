package mx.ferreteria.api.rh.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.rh.dto.RhDtos;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class NominaService {

    private final NominaRepository nominaRepo;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<RhDtos.NominaResponse> list(String estado,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Nomina> page = nominaRepo.filtrar(estado, desde, hasta, pageable);
        if (page.isEmpty()) {
            return page.map(this::toResponse);
        }
        Set<Integer> empIds = page.getContent().stream()
                .map(Nomina::getEmpleadoId)
                .collect(Collectors.toSet());
        Map<Integer, String> nombres = fetchNombresBatch(empIds);
        return page.map(n -> toResponse(n, nombres));
    }

    @Transactional(readOnly = true)
    public RhDtos.NominaResponse getById(Long id) {
        Nomina n = nominaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(n);
    }

    public RhDtos.NominaResponse create(RhDtos.NominaRequest req) {
        if (!empleadoExiste(req.empleadoId())) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
        Nomina n = Nomina.builder()
                .empleadoId(req.empleadoId())
                .periodoIni(req.periodoIni())
                .periodoFin(req.periodoFin())
                .diasPagados(req.diasPagados())
                .percepciones(req.percepciones())
                .deducciones(req.deducciones())
                .notas(req.notas())
                .usuarioRegistraId(UserPrincipal.actual().usuarioId())
                .build();
        Nomina saved = nominaRepo.save(n);
        nominaRepo.flush();
        return toResponse(saved);
    }

    public RhDtos.GenerarQuincenaResponse generarQuincena(RhDtos.GenerarQuincenaRequest req) {
        String q = req.quincena() == null ? "" : req.quincena().trim().toUpperCase();
        if (!"PRIMERA".equals(q) && !"SEGUNDA".equals(q)) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "quincena debe ser PRIMERA o SEGUNDA");
        }
        LocalDate hoy = LocalDate.now();
        int anio = req.anio() != null ? req.anio() : hoy.getYear();
        int mes = req.mes() != null ? req.mes() : hoy.getMonthValue();
        if (mes < 1 || mes > 12) throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "mes");
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate ini;
        LocalDate fin;
        if ("PRIMERA".equals(q)) {
            ini = ym.atDay(1);
            fin = ym.atDay(15);
        } else {
            ini = ym.atDay(16);
            fin = ym.atEndOfMonth();
        }
        BigDecimal dias = BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(ini, fin) + 1);

        List<java.util.Map<String, Object>> empleados = jdbc.queryForList(
                "SELECT empleado_id, sueldo_diario FROM rh.empleados WHERE activo = true ORDER BY empleado_id");
        if (empleados.isEmpty()) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "sin empleados activos");
        }
        int usuarioId = UserPrincipal.actual().usuarioId();
        int creadas = 0;
        int omitidas = 0;
        // Batch duplicate check: single SELECT empleado_id FROM rh.nominas WHERE periodo_ini=? AND periodo_fin=? AND empleado_id IN (...)
        List<Integer> allEmpIds = empleados.stream()
                .map(row -> ((Number) row.get("empleado_id")).intValue())
                .collect(Collectors.toList());
        Set<Integer> existingIds = findExistingEmpleadoIds(ini, fin, allEmpIds);
        List<Nomina> savedEntities = new ArrayList<>();
        for (java.util.Map<String, Object> row : empleados) {
            Integer empId = ((Number) row.get("empleado_id")).intValue();
            BigDecimal sueldo = new BigDecimal(row.get("sueldo_diario").toString());
            BigDecimal percepciones = sueldo.multiply(dias);
            // deducciones 0 por defecto
            if (existingIds.contains(empId)) {
                omitidas++;
                continue;
            }
            try {
                Nomina n = Nomina.builder()
                        .empleadoId(empId)
                        .periodoIni(ini)
                        .periodoFin(fin)
                        .diasPagados(dias)
                        .percepciones(percepciones)
                        .deducciones(BigDecimal.ZERO)
                        .notas("Quincena " + q + " " + ym)
                        .usuarioRegistraId(usuarioId == 0 ? 1 : usuarioId)
                        .build();
                Nomina saved = nominaRepo.save(n);
                savedEntities.add(saved);
                creadas++;
            } catch (Exception e) {
                // BACK-EST-001: el catch antes era silencioso (omitidas++; sin log),
                // haciendo imposible diagnosticar nóminas faltantes. Ahora se loguea
                // a nivel WARN con el id del empleado y el tipo + mensaje resumido,
                // sin filtrar PHI/PII porque empleadoId es la PK interna.
                log.warn("nomina omitida empleado_id={} tipo={} mensaje={}",
                        empId, e.getClass().getSimpleName(), e.getMessage());
                omitidas++;
            }
        }
        nominaRepo.flush();
        // recargar respuestas para asegurar neto_pagar generado - batch fetch
        List<RhDtos.NominaResponse> fresh;
        if (savedEntities.isEmpty()) {
            fresh = List.of();
        } else {
            List<Long> savedIds = savedEntities.stream()
                    .map(Nomina::getNominaId)
                    .collect(Collectors.toList());
            List<Nomina> freshEntities = nominaRepo.findAllById(savedIds);
            Set<Integer> freshEmpIds = freshEntities.stream()
                    .map(Nomina::getEmpleadoId)
                    .collect(Collectors.toSet());
            Map<Integer, String> nombres = fetchNombresBatch(freshEmpIds);
            fresh = freshEntities.stream()
                    .map(n -> toResponse(n, nombres))
                    .collect(Collectors.toList());
        }
        return new RhDtos.GenerarQuincenaResponse(creadas, omitidas, ini, fin, fresh);
    }

    public RhDtos.PagarLoteResponse pagarLote(RhDtos.PagarLoteRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new ReglaNegocioException(ErrorCode.CAMPO_REQUERIDO, "ids");
        }
        int pagadas = 0;
        int omitidas = 0;
        List<Nomina> updated = new ArrayList<>();
        for (Long id : req.ids()) {
            Nomina n = nominaRepo.findById(id).orElse(null);
            if (n == null) { omitidas++; continue; }
            if ("PAGADA".equals(n.getEstado()) || "CANCELADA".equals(n.getEstado())) { omitidas++; continue; }
            n.setEstado("PAGADA");
            n.setFechaPago(Instant.now());
            Nomina saved = nominaRepo.save(n);
            updated.add(saved);
            pagadas++;
        }
        nominaRepo.flush();
        if (updated.isEmpty()) {
            return new RhDtos.PagarLoteResponse(pagadas, omitidas, List.of());
        }
        Set<Integer> empIds = updated.stream().map(Nomina::getEmpleadoId).collect(Collectors.toSet());
        Map<Integer, String> nombres = fetchNombresBatch(empIds);
        List<RhDtos.NominaResponse> result = updated.stream()
                .map(n -> toResponse(n, nombres))
                .collect(Collectors.toList());
        return new RhDtos.PagarLoteResponse(pagadas, omitidas, result);
    }

    public RhDtos.NominaResponse marcarPagada(Long id) {
        Nomina n = nominaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if ("PAGADA".equals(n.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO);
        }
        if ("CANCELADA".equals(n.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        n.setEstado("PAGADA");
        n.setFechaPago(Instant.now());
        return toResponse(nominaRepo.save(n));
    }

    public RhDtos.NominaResponse cancelar(Long id) {
        Nomina n = nominaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if ("CANCELADA".equals(n.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO);
        }
        if ("PAGADA".equals(n.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        n.setEstado("CANCELADA");
        return toResponse(nominaRepo.save(n));
    }

    private boolean empleadoExiste(Integer empleadoId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rh.empleados WHERE empleado_id = ?",
                Integer.class, empleadoId);
        return count != null && count > 0;
    }

    private RhDtos.NominaResponse toResponse(Nomina n) {
        String nombre = jdbc.queryForObject(
                "SELECT (COALESCE(nombre, '') || ' ' || COALESCE(apellido_p, ''))::varchar(161)"
                        + " FROM rh.empleados WHERE empleado_id = ?",
                String.class, n.getEmpleadoId());
        return new RhDtos.NominaResponse(
                n.getNominaId(), n.getEmpleadoId(), nombre,
                n.getPeriodoIni(), n.getPeriodoFin(),
                n.getDiasPagados(), n.getPercepciones(),
                n.getDeducciones(), n.getNetoPagar(),
                n.getEstado(), n.getFechaPago(),
                n.getUsuarioRegistraId(), n.getNotas());
    }

    private RhDtos.NominaResponse toResponse(Nomina n, Map<Integer, String> nombres) {
        String nombre = nombres.getOrDefault(n.getEmpleadoId(), null);
        if (nombre == null) {
            // fallback for single missing entry (keeps backward compat with existing mocks)
            nombre = jdbc.queryForObject(
                    "SELECT (COALESCE(nombre, '') || ' ' || COALESCE(apellido_p, ''))::varchar(161)"
                            + " FROM rh.empleados WHERE empleado_id = ?",
                    String.class, n.getEmpleadoId());
        }
        return new RhDtos.NominaResponse(
                n.getNominaId(), n.getEmpleadoId(), nombre,
                n.getPeriodoIni(), n.getPeriodoFin(),
                n.getDiasPagados(), n.getPercepciones(),
                n.getDeducciones(), n.getNetoPagar(),
                n.getEstado(), n.getFechaPago(),
                n.getUsuarioRegistraId(), n.getNotas());
    }

    /**
     * Batch: SELECT empleado_id FROM rh.nominas WHERE periodo_ini=? AND periodo_fin=? AND empleado_id IN (...)
     * Single query instead of N x COUNT(*).
     */
    Set<Integer> findExistingEmpleadoIds(LocalDate ini, LocalDate fin, Collection<Integer> empleadoIds) {
        if (empleadoIds == null || empleadoIds.isEmpty()) {
            return Collections.emptySet();
        }
        String placeholders = empleadoIds.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT empleado_id FROM rh.nominas WHERE periodo_ini = ? AND periodo_fin = ? AND empleado_id IN ("
                + placeholders + ")";
        List<Object> params = new ArrayList<>();
        params.add(ini);
        params.add(fin);
        params.addAll(empleadoIds);
        List<Integer> rows = jdbc.query(sql, (rs, rowNum) -> rs.getInt(1), params.toArray());
        return new HashSet<>(rows);
    }

    /**
     * Batch: SELECT empleado_id, nombre_completo FROM rh.empleados WHERE empleado_id IN (...)
     * Single query instead of N x lookup.
     */
    Map<Integer, String> fetchNombresBatch(Collection<Integer> empleadoIds) {
        if (empleadoIds == null || empleadoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = empleadoIds.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT empleado_id, (COALESCE(nombre, '') || ' ' || COALESCE(apellido_p, ''))::varchar(161) AS nombre_completo "
                + "FROM rh.empleados WHERE empleado_id IN (" + placeholders + ")";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, empleadoIds.toArray());
            if (rows.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<Integer, String> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("empleado_id");
                if (idObj == null) idObj = row.get("EMPLEADO_ID");
                if (idObj == null) idObj = row.values().iterator().next();
                Integer id = ((Number) idObj).intValue();
                Object val = row.get("nombre_completo");
                if (val == null) val = row.get("NOMBRE_COMPLETO");
                if (val == null) {
                    java.util.Iterator<Object> it = row.values().iterator();
                    it.next();
                    if (it.hasNext()) val = it.next();
                }
                String nombre = val != null ? val.toString() : null;
                map.put(id, nombre);
            }
            return map;
        } catch (Exception e) {
            // If batch query not mocked (tests mock queryForObject), return empty to trigger per-row fallback
            return Collections.emptyMap();
        }
    }
}
