package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.RegimenFiscalRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.RegimenFiscalResponse;
import mx.ferreteria.api.cat.entity.RegimenFiscal;
import mx.ferreteria.api.cat.repo.RegimenFiscalRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class RegimenFiscalService extends AbstractCatalogoService<RegimenFiscal, String, RegimenFiscalRequest, RegimenFiscalResponse> {

    private final RegimenFiscalRepository repo;

    @Override
    protected JpaRepository<RegimenFiscal, String> repo() { return repo; }

    @Override
    protected RegimenFiscal toEntity(RegimenFiscalRequest req) {
        return RegimenFiscal.builder()
                .claveSat(req.claveSat())
                .descripcion(req.descripcion())
                .personaFisica(Boolean.TRUE.equals(req.personaFisica()) || req.personaFisica() == null)
                .personaMoral(Boolean.TRUE.equals(req.personaMoral()) || req.personaMoral() == null)
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(RegimenFiscal entity, RegimenFiscalRequest req) {
        entity.setClaveSat(req.claveSat());
        entity.setDescripcion(req.descripcion());
        entity.setPersonaFisica(Boolean.TRUE.equals(req.personaFisica()) || req.personaFisica() == null);
        entity.setPersonaMoral(Boolean.TRUE.equals(req.personaMoral()) || req.personaMoral() == null);
    }

    @Override
    protected RegimenFiscalResponse toResponse(RegimenFiscal r) {
        return new RegimenFiscalResponse(
                r.getClaveSat(), r.getDescripcion(), r.getPersonaFisica(), r.getPersonaMoral(), r.getActivo());
    }

    @Override
    protected String extractId(RegimenFiscal entity) { return entity.getClaveSat(); }

    @Override
    protected void deactivateEntity(RegimenFiscal entity) {
        entity.setActivo(false);
    }
}
