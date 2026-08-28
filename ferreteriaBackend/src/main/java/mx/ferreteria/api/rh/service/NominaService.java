package mx.ferreteria.api.rh.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
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
    public Page<RhDtos.NominaResponse> list(String estado, Pageable pageable) {
        Page<Nomina> page = (estado != null && !estado.isBlank())
                ? nominaRepo.findByEstadoOrderByPeriodoFinDesc(estado, pageable)
                : nominaRepo.findAllByOrderByPeriodoFinDesc(pageable);
        return page.map(this::toResponse);
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
                .usuarioRegistraId(1)
                .build();
        Nomina saved = nominaRepo.save(n);
        nominaRepo.flush();
        return toResponse(saved);
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