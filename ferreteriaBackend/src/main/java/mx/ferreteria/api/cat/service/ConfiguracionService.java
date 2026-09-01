package mx.ferreteria.api.cat.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ConfiguracionRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ConfiguracionResponse;
import mx.ferreteria.api.cat.entity.Configuracion;
import mx.ferreteria.api.cat.repo.ConfiguracionRepository;

@Service
@RequiredArgsConstructor
public class ConfiguracionService extends AbstractCatalogoService<Configuracion, String, ConfiguracionRequest, ConfiguracionResponse> {

    private final ConfiguracionRepository repo;

    @Override
    protected JpaRepository<Configuracion, String> repo() { return repo; }

    @Override
    protected Configuracion toEntity(ConfiguracionRequest req) {
        return Configuracion.builder()
                .clave(req.clave())
                .valor(req.valor())
                .descripcion(req.descripcion())
                .build();
    }

    @Override
    protected void updateEntity(Configuracion entity, ConfiguracionRequest req) {
        entity.setClave(req.clave());
        entity.setValor(req.valor());
        entity.setDescripcion(req.descripcion());
    }

    @Override
    protected ConfiguracionResponse toResponse(Configuracion c) {
        return new ConfiguracionResponse(c.getClave(), c.getValor(), c.getDescripcion());
    }

    @Override
    protected String extractId(Configuracion entity) { return entity.getClave(); }
}
