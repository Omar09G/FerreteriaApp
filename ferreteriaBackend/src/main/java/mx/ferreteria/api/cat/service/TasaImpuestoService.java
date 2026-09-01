package mx.ferreteria.api.cat.service;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TasaImpuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TasaImpuestoResponse;
import mx.ferreteria.api.cat.entity.Impuesto;
import mx.ferreteria.api.cat.entity.TasaImpuesto;
import mx.ferreteria.api.cat.repo.ImpuestoRepository;
import mx.ferreteria.api.cat.repo.TasaImpuestoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class TasaImpuestoService extends AbstractCatalogoService<TasaImpuesto, Integer, TasaImpuestoRequest, TasaImpuestoResponse> {

    private final TasaImpuestoRepository repo;
    private final ImpuestoRepository impuestoRepo;

    @Override
    protected JpaRepository<TasaImpuesto, Integer> repo() { return repo; }

    @Override
    protected TasaImpuesto toEntity(TasaImpuestoRequest req) {
        Impuesto impuesto = impuestoRepo.findById(req.impuestoId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, "Impuesto"));
        return TasaImpuesto.builder()
                .impuesto(impuesto)
                .tasa(req.tasa())
                .factor(req.factor())
                .ambito(req.ambito())
                .zonaFrontera(Boolean.TRUE.equals(req.zonaFrontera()))
                .vigenteDesde(req.vigenteDesde() != null ? req.vigenteDesde() : LocalDate.now())
                .vigenteHasta(req.vigenteHasta())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(TasaImpuesto entity, TasaImpuestoRequest req) {
        Impuesto impuesto = impuestoRepo.findById(req.impuestoId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, "Impuesto"));
        entity.setImpuesto(impuesto);
        entity.setTasa(req.tasa());
        entity.setFactor(req.factor());
        entity.setAmbito(req.ambito());
        entity.setZonaFrontera(Boolean.TRUE.equals(req.zonaFrontera()));
        entity.setVigenteDesde(req.vigenteDesde() != null ? req.vigenteDesde() : LocalDate.now());
        entity.setVigenteHasta(req.vigenteHasta());
    }

    @Override
    protected TasaImpuestoResponse toResponse(TasaImpuesto t) {
        return new TasaImpuestoResponse(
                t.getTasaId(), t.getImpuesto().getImpuestoId(), t.getImpuesto().getNombre(),
                t.getTasa(), t.getFactor(), t.getAmbito(), t.getZonaFrontera(),
                t.getVigenteDesde(), t.getVigenteHasta(), t.getActivo());
    }

    @Override
    protected Integer extractId(TasaImpuesto entity) { return entity.getTasaId(); }

    @Override
    protected void deactivateEntity(TasaImpuesto entity) {
        entity.setActivo(false);
    }
}
