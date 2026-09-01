package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.EstadoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.EstadoResponse;
import mx.ferreteria.api.cat.entity.Estado;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.EstadoService;

@RestController
@RequestMapping("/api/v1/estados")
@RequiredArgsConstructor
public class EstadoController extends AbstractCatalogoController<Estado, Integer, EstadoRequest, EstadoResponse> {

    private final EstadoService service;

    @Override
    protected AbstractCatalogoService<Estado, Integer, EstadoRequest, EstadoResponse> service() { return service; }
}
