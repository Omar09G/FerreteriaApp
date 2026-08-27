package mx.ferreteria.api.cat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.CategoriaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.CategoriaResponse;
import mx.ferreteria.api.cat.entity.Categoria;
import mx.ferreteria.api.cat.repo.CategoriaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaService {

    private final CategoriaRepository repo;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listTree() {
        List<Categoria> roots = repo.findByActivoTrueAndCategoriaPadreIsNullOrderByNombre();
        List<CategoriaResponse> result = new ArrayList<>();
        for (Categoria root : roots) {
            result.add(toTreeResponse(root));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponse> list(String q, Pageable pageable) {
        return repo.findByActivoTrue(pageable).map(this::toFlatResponse);
    }

    @Transactional(readOnly = true)
    public CategoriaResponse getById(Integer id) {
        Categoria entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toFlatResponse(entity);
    }

    public CategoriaResponse create(CategoriaRequest req) {
        Categoria entity = Categoria.builder()
                .nombre(req.nombre())
                .nivel((short) 0)
                .build();

        if (req.categoriaPadreId() != null) {
            Categoria parent = repo.findById(req.categoriaPadreId()).orElseThrow(
                    () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
            entity.setCategoriaPadre(parent);
            entity.setNivel((short) (parent.getNivel() + 1));
        }

        return toFlatResponse(repo.save(entity));
    }

    public CategoriaResponse update(Integer id, CategoriaRequest req) {
        Categoria entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setNombre(req.nombre());

        if (req.categoriaPadreId() != null) {
            Categoria parent = repo.findById(req.categoriaPadreId()).orElseThrow(
                    () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
            entity.setCategoriaPadre(parent);
            entity.setNivel((short) (parent.getNivel() + 1));
        } else {
            entity.setCategoriaPadre(null);
            entity.setNivel((short) 0);
        }

        return toFlatResponse(repo.save(entity));
    }

    public void deactivate(Integer id) {
        Categoria entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private CategoriaResponse toTreeResponse(Categoria c) {
        List<CategoriaResponse> hijos = repo
                .findByCategoriaPadreCategoriaIdAndActivoTrueOrderByNombre(c.getCategoriaId())
                .stream()
                .map(this::toTreeResponse)
                .toList();
        return new CategoriaResponse(
                c.getCategoriaId(), c.getNombre(),
                c.getCategoriaPadre() != null ? c.getCategoriaPadre().getCategoriaId() : null,
                c.getRuta(), c.getNivel(), hijos);
    }

    private CategoriaResponse toFlatResponse(Categoria c) {
        return new CategoriaResponse(
                c.getCategoriaId(), c.getNombre(),
                c.getCategoriaPadre() != null ? c.getCategoriaPadre().getCategoriaId() : null,
                c.getRuta(), c.getNivel(), List.of());
    }
}
