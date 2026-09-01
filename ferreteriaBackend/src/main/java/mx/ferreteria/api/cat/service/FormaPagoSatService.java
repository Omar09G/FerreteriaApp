package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoSatResponse;
import mx.ferreteria.api.cat.entity.FormaPagoSat;
import mx.ferreteria.api.cat.repo.FormaPagoSatRepository;

@Service
@RequiredArgsConstructor
public class FormaPagoSatService extends AbstractCatalogoService<FormaPagoSat, String, FormaPagoSatRequest, FormaPagoSatResponse> {

    private final FormaPagoSatRepository repo;

    @Override
    protected JpaRepository<FormaPagoSat, String> repo() { return repo; }

    @Override
    protected FormaPagoSat toEntity(FormaPagoSatRequest req) {
        return FormaPagoSat.builder()
                .clave(req.clave())
                .descripcion(req.descripcion())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(FormaPagoSat entity, FormaPagoSatRequest req) {
        entity.setClave(req.clave());
        entity.setDescripcion(req.descripcion());
    }

    @Override
    protected FormaPagoSatResponse toResponse(FormaPagoSat f) {
        return new FormaPagoSatResponse(f.getClave(), f.getDescripcion(), f.getActivo());
    }

    @Override
    protected String extractId(FormaPagoSat entity) { return entity.getClave(); }

    @Override
    protected void deactivateEntity(FormaPagoSat entity) {
        entity.setActivo(false);
    }
}
