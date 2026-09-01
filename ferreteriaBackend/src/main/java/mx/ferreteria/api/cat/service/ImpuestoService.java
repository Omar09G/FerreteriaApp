package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ImpuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ImpuestoResponse;
import mx.ferreteria.api.cat.entity.Impuesto;
import mx.ferreteria.api.cat.repo.ImpuestoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class ImpuestoService extends AbstractCatalogoService<Impuesto, Integer, ImpuestoRequest, ImpuestoResponse> {

    private final ImpuestoRepository repo;

    @Override
    protected JpaRepository<Impuesto, Integer> repo() { return repo; }

    @Override
    protected Impuesto toEntity(ImpuestoRequest req) {
        return Impuesto.builder()
                .claveSat(req.claveSat())
                .nombre(req.nombre())
                .tipo(req.tipo())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(Impuesto entity, ImpuestoRequest req) {
        entity.setClaveSat(req.claveSat());
        entity.setNombre(req.nombre());
        entity.setTipo(req.tipo());
    }

    @Override
    protected ImpuestoResponse toResponse(Impuesto e) {
        return new ImpuestoResponse(e.getImpuestoId(), e.getClaveSat(), e.getNombre(), e.getTipo(), e.getActivo());
    }

    @Override
    protected Integer extractId(Impuesto entity) { return entity.getImpuestoId(); }

    @Override
    protected void validateCreate(Impuesto entity) {
        if (repo.existsByClaveSat(entity.getClaveSat())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Clave SAT");
        }
    }

    @Override
    protected void deactivateEntity(Impuesto entity) {
        entity.setActivo(false);
    }
}
