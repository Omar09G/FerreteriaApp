package mx.ferreteria.api.cat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaResponse;
import mx.ferreteria.api.cat.entity.UnidadMedida;
import mx.ferreteria.api.cat.repo.UnidadMedidaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class UnidadMedidaService {

    private final UnidadMedidaRepository repo;

    @Transactional(readOnly = true)
    public Page<UnidadMedidaResponse> list(String q, Pageable pageable) {
        Page<UnidadMedida> page = StringUtils.hasText(q)
                ? repo.findByActivoTrueAndNombreContainingIgnoreCase(q, pageable)
                : repo.findByActivoTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnidadMedidaResponse getById(Integer id) {
        UnidadMedida entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public UnidadMedidaResponse create(UnidadMedidaRequest req) {
        UnidadMedida entity = UnidadMedida.builder()
                .clave(req.clave())
                .nombre(req.nombre())
                .permiteFraccion(req.permiteFraccion() != null ? req.permiteFraccion() : false)
                .build();
        return toResponse(repo.save(entity));
    }

    public UnidadMedidaResponse update(Integer id, UnidadMedidaRequest req) {
        UnidadMedida entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setClave(req.clave());
        entity.setNombre(req.nombre());
        if (req.permiteFraccion() != null) {
            entity.setPermiteFraccion(req.permiteFraccion());
        }
        return toResponse(repo.save(entity));
    }

    public void deactivate(Integer id) {
        UnidadMedida entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private UnidadMedidaResponse toResponse(UnidadMedida u) {
        return new UnidadMedidaResponse(
                u.getUnidadId(), u.getClave(), u.getNombre(), u.getPermiteFraccion());
    }
}
