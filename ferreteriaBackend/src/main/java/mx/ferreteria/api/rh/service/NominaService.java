package mx.ferreteria.api.rh.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
@Transactional
public class NominaService {

    private final NominaRepository nominaRepo;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<RhDtos.NominaResponse> list(String estado,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        return nominaRepo.filtrar(estado, desde, hasta, pageable).map(this::toResponse);
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
        List<RhDtos.NominaResponse> result = new ArrayList<>();
        for (java.util.Map<String, Object> row : empleados) {
            Integer empId = ((Number) row.get("empleado_id")).intValue();
            BigDecimal sueldo = new BigDecimal(row.get("sueldo_diario").toString());
            BigDecimal percepciones = sueldo.multiply(dias);
            // deducciones 0 por defecto
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rh.nominas WHERE empleado_id=? AND periodo_ini=? AND periodo_fin=?",
                    Integer.class, empId, ini, fin);
            if (existe != null && existe > 0) {
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
                // flush en bloque al final
                result.add(toResponse(saved));
                creadas++;
            } catch (Exception e) {
                // duplicado concurrente
                omitidas++;
            }
        }
        nominaRepo.flush();
        // recargar respuestas para asegurar neto_pagar generado
        List<RhDtos.NominaResponse> fresh = new ArrayList<>();
        for (RhDtos.NominaResponse r : result) {
            fresh.add(getById(r.nominaId()));
        }
        return new RhDtos.GenerarQuincenaResponse(creadas, omitidas, ini, fin, fresh);
    }

    public RhDtos.PagarLoteResponse pagarLote(RhDtos.PagarLoteRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new ReglaNegocioException(ErrorCode.CAMPO_REQUERIDO, "ids");
        }
        int pagadas = 0;
        int omitidas = 0;
        List<RhDtos.NominaResponse> result = new ArrayList<>();
        for (Long id : req.ids()) {
            Nomina n = nominaRepo.findById(id).orElse(null);
            if (n == null) { omitidas++; continue; }
            if ("PAGADA".equals(n.getEstado()) || "CANCELADA".equals(n.getEstado())) { omitidas++; continue; }
            n.setEstado("PAGADA");
            n.setFechaPago(Instant.now());
            Nomina saved = nominaRepo.save(n);
            result.add(toResponse(saved));
            pagadas++;
        }
        nominaRepo.flush();
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
}