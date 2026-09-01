package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UnidadSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UnidadSatResponse;
import mx.ferreteria.api.cat.entity.UnidadSat;
import mx.ferreteria.api.cat.repo.UnidadSatRepository;

@Service
@RequiredArgsConstructor
public class UnidadSatService extends AbstractCatalogoService<UnidadSat, String, UnidadSatRequest, UnidadSatResponse> {

    private final UnidadSatRepository repo;

    @Override
    protected JpaRepository<UnidadSat, String> repo() { return repo; }

    @Override
    protected UnidadSat toEntity(UnidadSatRequest req) {
        return UnidadSat.builder()
                .clave(req.clave())
                .descripcion(req.descripcion())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(UnidadSat entity, UnidadSatRequest req) {
        entity.setClave(req.clave());
        entity.setDescripcion(req.descripcion());
    }

    @Override
    protected UnidadSatResponse toResponse(UnidadSat u) {
        return new UnidadSatResponse(u.getClave(), u.getDescripcion(), u.getActivo());
    }

    @Override
    protected String extractId(UnidadSat entity) { return entity.getClave(); }

    @Override
    protected void deactivateEntity(UnidadSat entity) {
        entity.setActivo(false);
    }
}
