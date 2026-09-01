package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoResponse;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.FormaPagoSat;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.FormaPagoSatRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class FormaPagoService extends AbstractCatalogoService<FormaPago, Integer, FormaPagoRequest, FormaPagoResponse> {

    private final FormaPagoRepository repo;
    private final FormaPagoSatRepository fpSatRepo;

    @Override
    protected JpaRepository<FormaPago, Integer> repo() { return repo; }

    @Override
    protected FormaPago toEntity(FormaPagoRequest req) {
        FormaPagoSat sat = null;
        if (req.formaPagoSatClave() != null) {
            sat = fpSatRepo.findById(req.formaPagoSatClave()).orElse(null);
        }
        return FormaPago.builder()
                .clave(req.clave())
                .nombre(req.nombre())
                .esEfectivo(Boolean.TRUE.equals(req.esEfectivo()))
                .requiereReferencia(Boolean.TRUE.equals(req.requiereReferencia()))
                .afectaCaja(Boolean.TRUE.equals(req.afectaCaja()) || req.afectaCaja() == null)
                .formaPagoSat(sat)
                .comisionPct(req.comisionPct() != null ? req.comisionPct() : java.math.BigDecimal.ZERO)
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(FormaPago entity, FormaPagoRequest req) {
        entity.setClave(req.clave());
        entity.setNombre(req.nombre());
        entity.setEsEfectivo(Boolean.TRUE.equals(req.esEfectivo()));
        entity.setRequiereReferencia(Boolean.TRUE.equals(req.requiereReferencia()));
        entity.setAfectaCaja(Boolean.TRUE.equals(req.afectaCaja()) || req.afectaCaja() == null);
        if (req.formaPagoSatClave() != null) {
            entity.setFormaPagoSat(fpSatRepo.findById(req.formaPagoSatClave()).orElse(null));
        }
        if (req.comisionPct() != null) entity.setComisionPct(req.comisionPct());
    }

    @Override
    protected FormaPagoResponse toResponse(FormaPago f) {
        return new FormaPagoResponse(
                f.getFormaPagoId(), f.getClave(), f.getNombre(),
                f.getEsEfectivo(), f.getRequiereReferencia(), f.getAfectaCaja(),
                f.getFormaPagoSat() != null ? f.getFormaPagoSat().getClave() : null,
                f.getComisionPct(), f.getActivo());
    }

    @Override
    protected Integer extractId(FormaPago entity) { return entity.getFormaPagoId(); }

    @Override
    protected void validateCreate(FormaPago entity) {
        if (repo.existsByClave(entity.getClave())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Clave");
        }
    }

    @Override
    protected void deactivateEntity(FormaPago entity) {
        entity.setActivo(false);
    }
}
