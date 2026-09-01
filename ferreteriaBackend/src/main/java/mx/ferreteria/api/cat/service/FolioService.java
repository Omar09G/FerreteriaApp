package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FolioRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FolioResponse;
import mx.ferreteria.api.cat.entity.Folio;
import mx.ferreteria.api.cat.repo.FolioRepository;

@Service
@RequiredArgsConstructor
public class FolioService extends AbstractCatalogoService<Folio, String, FolioRequest, FolioResponse> {

    private final FolioRepository repo;

    @Override
    protected JpaRepository<Folio, String> repo() { return repo; }

    @Override
    protected Folio toEntity(FolioRequest req) {
        return Folio.builder()
                .tipo(req.tipo())
                .prefijo(req.prefijo())
                .consecutivo(req.consecutivo() != null ? req.consecutivo() : 0L)
                .build();
    }

    @Override
    protected void updateEntity(Folio entity, FolioRequest req) {
        entity.setTipo(req.tipo());
        entity.setPrefijo(req.prefijo());
        entity.setConsecutivo(req.consecutivo() != null ? req.consecutivo() : 0L);
    }

    @Override
    protected FolioResponse toResponse(Folio f) {
        return new FolioResponse(f.getTipo(), f.getPrefijo(), f.getConsecutivo());
    }

    @Override
    protected String extractId(Folio entity) { return entity.getTipo(); }
}
