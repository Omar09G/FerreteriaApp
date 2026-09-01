package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TipoGastoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.TipoGastoResponse;
import mx.ferreteria.api.cat.entity.TipoGasto;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.TipoGastoService;

@RestController
@RequestMapping("/api/v1/tipos-gasto")
@RequiredArgsConstructor
public class TipoGastoController extends AbstractCatalogoController<TipoGasto, Integer, TipoGastoRequest, TipoGastoResponse> {

    private final TipoGastoService service;

    @Override
    protected AbstractCatalogoService<TipoGasto, Integer, TipoGastoRequest, TipoGastoResponse> service() { return service; }
}
