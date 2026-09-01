package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UnidadSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UnidadSatResponse;
import mx.ferreteria.api.cat.entity.UnidadSat;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.UnidadSatService;

@RestController
@RequestMapping("/api/v1/unidades-sat")
@RequiredArgsConstructor
public class UnidadSatController extends AbstractCatalogoController<UnidadSat, String, UnidadSatRequest, UnidadSatResponse> {

    private final UnidadSatService service;

    @Override
    protected AbstractCatalogoService<UnidadSat, String, UnidadSatRequest, UnidadSatResponse> service() { return service; }
}
