package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MetodoPagoSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MetodoPagoSatResponse;
import mx.ferreteria.api.cat.entity.MetodoPagoSat;
import mx.ferreteria.api.cat.repo.MetodoPagoSatRepository;

@Service
@RequiredArgsConstructor
public class MetodoPagoSatService extends AbstractCatalogoService<MetodoPagoSat, String, MetodoPagoSatRequest, MetodoPagoSatResponse> {

    private final MetodoPagoSatRepository repo;

    @Override
    protected JpaRepository<MetodoPagoSat, String> repo() { return repo; }

    @Override
    protected MetodoPagoSat toEntity(MetodoPagoSatRequest req) {
        return MetodoPagoSat.builder()
                .clave(req.clave())
                .descripcion(req.descripcion())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(MetodoPagoSat entity, MetodoPagoSatRequest req) {
        entity.setClave(req.clave());
        entity.setDescripcion(req.descripcion());
    }

    @Override
    protected MetodoPagoSatResponse toResponse(MetodoPagoSat m) {
        return new MetodoPagoSatResponse(m.getClave(), m.getDescripcion(), m.getActivo());
    }

    @Override
    protected String extractId(MetodoPagoSat entity) { return entity.getClave(); }

    @Override
    protected void deactivateEntity(MetodoPagoSat entity) {
        entity.setActivo(false);
    }
}
