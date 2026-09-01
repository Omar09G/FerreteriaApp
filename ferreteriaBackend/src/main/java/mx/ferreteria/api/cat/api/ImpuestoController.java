package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ImpuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ImpuestoResponse;
import mx.ferreteria.api.cat.entity.Impuesto;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.ImpuestoService;

@RestController
@RequestMapping("/api/v1/impuestos")
@RequiredArgsConstructor
public class ImpuestoController extends AbstractCatalogoController<Impuesto, Integer, ImpuestoRequest, ImpuestoResponse> {

    private final ImpuestoService service;

    @Override
    protected AbstractCatalogoService<Impuesto, Integer, ImpuestoRequest, ImpuestoResponse> service() { return service; }
}
