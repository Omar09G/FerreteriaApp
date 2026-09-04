package mx.ferreteria.api.cat.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Servicio base genérico para CRUD de catálogos. Cada servicio concreto
 * extiende esta clase y proporciona las operaciones de conversión y
 * validación específicas de su entidad.
 */
@Transactional
public abstract class AbstractCatalogoService<T, ID, REQ, RES> {

    protected abstract JpaRepository<T, ID> repo();

    protected abstract T toEntity(REQ request);

    protected abstract void updateEntity(T entity, REQ request);

    protected abstract RES toResponse(T entity);

    protected abstract ID extractId(T entity);

    @Transactional(readOnly = true)
    public Page<RES> findAll(Pageable pageable) {
        return repo().findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RES findById(ID id) {
        T entity = repo().findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id));
        return toResponse(entity);
    }

    public RES create(REQ request) {
        T entity = toEntity(request);
        validateCreate(entity);
        return toResponse(repo().save(entity));
    }

    public RES update(ID id, REQ request) {
        T entity = repo().findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id));
        updateEntity(entity, request);
        validateUpdate(entity);
        return toResponse(repo().save(entity));
    }

    public void deactivate(ID id) {
        T entity = repo().findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id));
        deactivateEntity(entity);
        repo().save(entity);
    }

    protected void deactivateEntity(T entity) {
        throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE);
    }

    protected void validateCreate(T entity) {
    }

    protected void validateUpdate(T entity) {
    }
}
