package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ClaveProdServRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ClaveProdServResponse;
import mx.ferreteria.api.cat.entity.ClaveProdServ;
import mx.ferreteria.api.cat.repo.ClaveProdServRepository;

@Service
@RequiredArgsConstructor
public class ClaveProdServService extends AbstractCatalogoService<ClaveProdServ, String, ClaveProdServRequest, ClaveProdServResponse> {

    private final ClaveProdServRepository repo;

    @Override
    protected JpaRepository<ClaveProdServ, String> repo() { return repo; }

    @Override
    protected ClaveProdServ toEntity(ClaveProdServRequest req) {
        return ClaveProdServ.builder()
                .clave(req.clave())
                .descripcion(req.descripcion())
                .incluyeIva(req.incluyeIva())
                .ejemplo(Boolean.TRUE.equals(req.ejemplo()))
                .build();
    }

    @Override
    protected void updateEntity(ClaveProdServ entity, ClaveProdServRequest req) {
        entity.setClave(req.clave());
        entity.setDescripcion(req.descripcion());
        entity.setIncluyeIva(req.incluyeIva());
        entity.setEjemplo(Boolean.TRUE.equals(req.ejemplo()));
    }

    @Override
    protected ClaveProdServResponse toResponse(ClaveProdServ c) {
        return new ClaveProdServResponse(c.getClave(), c.getDescripcion(), c.getIncluyeIva(), c.getEjemplo());
    }

    @Override
    protected String extractId(ClaveProdServ entity) { return entity.getClave(); }
}
