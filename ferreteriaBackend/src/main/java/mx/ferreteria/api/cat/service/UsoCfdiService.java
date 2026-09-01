package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UsoCfdiRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UsoCfdiResponse;
import mx.ferreteria.api.cat.entity.UsoCfdi;
import mx.ferreteria.api.cat.repo.UsoCfdiRepository;

@Service
@RequiredArgsConstructor
public class UsoCfdiService extends AbstractCatalogoService<UsoCfdi, String, UsoCfdiRequest, UsoCfdiResponse> {

    private final UsoCfdiRepository repo;

    @Override
    protected JpaRepository<UsoCfdi, String> repo() { return repo; }

    @Override
    protected UsoCfdi toEntity(UsoCfdiRequest req) {
        return UsoCfdi.builder()
                .clave(req.clave())
                .descripcion(req.descripcion())
                .aplicaFisica(Boolean.TRUE.equals(req.aplicaFisica()) || req.aplicaFisica() == null)
                .aplicaMoral(Boolean.TRUE.equals(req.aplicaMoral()) || req.aplicaMoral() == null)
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(UsoCfdi entity, UsoCfdiRequest req) {
        entity.setClave(req.clave());
        entity.setDescripcion(req.descripcion());
        entity.setAplicaFisica(Boolean.TRUE.equals(req.aplicaFisica()) || req.aplicaFisica() == null);
        entity.setAplicaMoral(Boolean.TRUE.equals(req.aplicaMoral()) || req.aplicaMoral() == null);
    }

    @Override
    protected UsoCfdiResponse toResponse(UsoCfdi u) {
        return new UsoCfdiResponse(
                u.getClave(), u.getDescripcion(), u.getAplicaFisica(), u.getAplicaMoral(), u.getActivo());
    }

    @Override
    protected String extractId(UsoCfdi entity) { return entity.getClave(); }

    @Override
    protected void deactivateEntity(UsoCfdi entity) {
        entity.setActivo(false);
    }
}
