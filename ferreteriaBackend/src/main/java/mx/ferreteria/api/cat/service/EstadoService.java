package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.EstadoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.EstadoResponse;
import mx.ferreteria.api.cat.entity.Estado;
import mx.ferreteria.api.cat.repo.EstadoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class EstadoService extends AbstractCatalogoService<Estado, Integer, EstadoRequest, EstadoResponse> {

    private final EstadoRepository estadoRepo;

    @Override
    protected JpaRepository<Estado, Integer> repo() { return estadoRepo; }

    @Override
    protected Estado toEntity(EstadoRequest req) {
        return Estado.builder()
                .claveInegi(req.claveInegi())
                .nombre(req.nombre())
                .build();
    }

    @Override
    protected void updateEntity(Estado entity, EstadoRequest req) {
        entity.setClaveInegi(req.claveInegi());
        entity.setNombre(req.nombre());
    }

    @Override
    protected EstadoResponse toResponse(Estado e) {
        return new EstadoResponse(e.getEstadoId(), e.getClaveInegi(), e.getNombre());
    }

    @Override
    protected Integer extractId(Estado entity) { return entity.getEstadoId(); }

    @Override
    protected void validateCreate(Estado entity) {
        if (estadoRepo.existsByClaveInegi(entity.getClaveInegi())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Clave INEGI");
        }
    }
}
