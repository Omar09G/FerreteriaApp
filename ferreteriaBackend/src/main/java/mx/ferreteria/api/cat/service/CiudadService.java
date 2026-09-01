package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.CiudadRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.CiudadResponse;
import mx.ferreteria.api.cat.entity.Ciudad;
import mx.ferreteria.api.cat.entity.Estado;
import mx.ferreteria.api.cat.repo.CiudadRepository;
import mx.ferreteria.api.cat.repo.EstadoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
public class CiudadService extends AbstractCatalogoService<Ciudad, Integer, CiudadRequest, CiudadResponse> {

    private final CiudadRepository ciudadRepo;
    private final EstadoRepository estadoRepo;

    @Override
    protected JpaRepository<Ciudad, Integer> repo() { return ciudadRepo; }

    @Override
    protected Ciudad toEntity(CiudadRequest req) {
        Estado estado = estadoRepo.findById(req.estadoId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, "Estado"));
        return Ciudad.builder()
                .estado(estado)
                .nombre(req.nombre())
                .build();
    }

    @Override
    protected void updateEntity(Ciudad entity, CiudadRequest req) {
        Estado estado = estadoRepo.findById(req.estadoId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, "Estado"));
        entity.setEstado(estado);
        entity.setNombre(req.nombre());
    }

    @Override
    protected CiudadResponse toResponse(Ciudad c) {
        return new CiudadResponse(
                c.getCiudadId(),
                c.getEstado().getEstadoId(),
                c.getEstado().getNombre(),
                c.getNombre());
    }

    @Override
    protected Integer extractId(Ciudad entity) { return entity.getCiudadId(); }

    @Override
    protected void validateCreate(Ciudad entity) {
        if (ciudadRepo.existsByEstadoEstadoIdAndNombre(
                entity.getEstado().getEstadoId(), entity.getNombre())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, "Ciudad");
        }
    }
}
