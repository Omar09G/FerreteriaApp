package mx.ferreteria.api.cat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.MarcaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.MarcaResponse;
import mx.ferreteria.api.cat.entity.Marca;
import mx.ferreteria.api.cat.repo.MarcaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class MarcaService {

    private final MarcaRepository repo;

    @Transactional(readOnly = true)
    public Page<MarcaResponse> list(String q, Pageable pageable) {
        Page<Marca> page = StringUtils.hasText(q)
                ? repo.findByActivoTrueAndNombreContainingIgnoreCase(q, pageable)
                : repo.findByActivoTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MarcaResponse getById(Integer id) {
        Marca entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public MarcaResponse create(MarcaRequest req) {
        Marca entity = Marca.builder()
                .nombre(req.nombre())
                .build();
        return toResponse(repo.save(entity));
    }

    public MarcaResponse update(Integer id, MarcaRequest req) {
        Marca entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setNombre(req.nombre());
        return toResponse(repo.save(entity));
    }

    public void deactivate(Integer id) {
        Marca entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private MarcaResponse toResponse(Marca m) {
        return new MarcaResponse(m.getMarcaId(), m.getNombre());
    }
}
