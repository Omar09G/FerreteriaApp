package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TipoGastoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TipoGastoResponse;
import mx.ferreteria.api.cat.entity.TipoGasto;
import mx.ferreteria.api.cat.repo.TipoGastoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class TipoGastoService extends AbstractCatalogoService<TipoGasto, Integer, TipoGastoRequest, TipoGastoResponse> {

    private final TipoGastoRepository repo;

    @Override
    protected JpaRepository<TipoGasto, Integer> repo() { return repo; }

    @Override
    protected TipoGasto toEntity(TipoGastoRequest req) {
        return TipoGasto.builder()
                .clave(req.clave())
                .nombre(req.nombre())
                .esFijo(Boolean.TRUE.equals(req.esFijo()))
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(TipoGasto entity, TipoGastoRequest req) {
        entity.setClave(req.clave());
        entity.setNombre(req.nombre());
        entity.setEsFijo(Boolean.TRUE.equals(req.esFijo()));
    }

    @Override
    protected TipoGastoResponse toResponse(TipoGasto t) {
        return new TipoGastoResponse(t.getTipoGastoId(), t.getClave(), t.getNombre(), t.getEsFijo(), t.getActivo());
    }

    @Override
    protected Integer extractId(TipoGasto entity) { return entity.getTipoGastoId(); }

    @Override
    protected void validateCreate(TipoGasto entity) {
        if (repo.existsByClave(entity.getClave())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Clave");
        }
    }

    @Override
    protected void deactivateEntity(TipoGasto entity) {
        entity.setActivo(false);
    }
}
