package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.PuestoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.PuestoResponse;
import mx.ferreteria.api.cat.entity.Puesto;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.PuestoService;

@RestController
@RequestMapping("/api/v1/puestos")
@RequiredArgsConstructor
public class PuestoController extends AbstractCatalogoController<Puesto, Integer, PuestoRequest, PuestoResponse> {

    private final PuestoService service;

    @Override
    protected AbstractCatalogoService<Puesto, Integer, PuestoRequest, PuestoResponse> service() { return service; }
}
