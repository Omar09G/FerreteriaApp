package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TasaImpuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TasaImpuestoResponse;
import mx.ferreteria.api.cat.entity.TasaImpuesto;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.TasaImpuestoService;

@RestController
@RequestMapping("/api/v1/tasas-impuesto")
@RequiredArgsConstructor
public class TasaImpuestoController extends AbstractCatalogoController<TasaImpuesto, Integer, TasaImpuestoRequest, TasaImpuestoResponse> {

    private final TasaImpuestoService service;

    @Override
    protected AbstractCatalogoService<TasaImpuesto, Integer, TasaImpuestoRequest, TasaImpuestoResponse> service() { return service; }
}
