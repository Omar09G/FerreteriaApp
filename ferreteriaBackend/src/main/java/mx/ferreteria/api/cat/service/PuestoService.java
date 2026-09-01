package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.PuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.PuestoResponse;
import mx.ferreteria.api.cat.entity.Puesto;
import mx.ferreteria.api.cat.repo.PuestoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class PuestoService extends AbstractCatalogoService<Puesto, Integer, PuestoRequest, PuestoResponse> {

    private final PuestoRepository puestoRepo;

    @Override
    protected JpaRepository<Puesto, Integer> repo() { return puestoRepo; }

    @Override
    protected Puesto toEntity(PuestoRequest req) {
        return Puesto.builder()
                .nombre(req.nombre())
                .sueldoBase(req.sueldoBase() != null ? req.sueldoBase() : java.math.BigDecimal.ZERO)
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(Puesto entity, PuestoRequest req) {
        entity.setNombre(req.nombre());
        if (req.sueldoBase() != null) entity.setSueldoBase(req.sueldoBase());
    }

    @Override
    protected PuestoResponse toResponse(Puesto p) {
        return new PuestoResponse(p.getPuestoId(), p.getNombre(), p.getSueldoBase(), p.getActivo());
    }

    @Override
    protected Integer extractId(Puesto entity) { return entity.getPuestoId(); }

    @Override
    protected void validateCreate(Puesto entity) {
        if (puestoRepo.existsByNombre(entity.getNombre())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Nombre del puesto");
        }
    }

    @Override
    protected void deactivateEntity(Puesto entity) {
        entity.setActivo(false);
    }
}
