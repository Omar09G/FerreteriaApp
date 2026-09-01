package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MotivoMovimientoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MotivoMovimientoResponse;
import mx.ferreteria.api.cat.entity.MotivoMovimiento;
import mx.ferreteria.api.cat.repo.MotivoMovimientoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class MotivoMovimientoService extends AbstractCatalogoService<MotivoMovimiento, Integer, MotivoMovimientoRequest, MotivoMovimientoResponse> {

    private final MotivoMovimientoRepository repo;

    @Override
    protected JpaRepository<MotivoMovimiento, Integer> repo() { return repo; }

    @Override
    protected MotivoMovimiento toEntity(MotivoMovimientoRequest req) {
        return MotivoMovimiento.builder()
                .clave(req.clave())
                .nombre(req.nombre())
                .tipoDefault(req.tipoDefault())
                .activo(true)
                .build();
    }

    @Override
    protected void updateEntity(MotivoMovimiento entity, MotivoMovimientoRequest req) {
        entity.setClave(req.clave());
        entity.setNombre(req.nombre());
        entity.setTipoDefault(req.tipoDefault());
    }

    @Override
    protected MotivoMovimientoResponse toResponse(MotivoMovimiento m) {
        return new MotivoMovimientoResponse(
                m.getMotivoId(), m.getClave(), m.getNombre(), m.getTipoDefault(), m.getActivo());
    }

    @Override
    protected Integer extractId(MotivoMovimiento entity) { return entity.getMotivoId(); }

    @Override
    protected void validateCreate(MotivoMovimiento entity) {
        if (repo.existsByClave(entity.getClave())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Clave");
        }
    }

    @Override
    protected void deactivateEntity(MotivoMovimiento entity) {
        entity.setActivo(false);
    }
}
