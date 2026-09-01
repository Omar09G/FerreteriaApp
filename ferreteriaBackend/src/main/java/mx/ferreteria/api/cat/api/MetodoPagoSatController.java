package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MetodoPagoSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MetodoPagoSatResponse;
import mx.ferreteria.api.cat.entity.MetodoPagoSat;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.MetodoPagoSatService;

@RestController
@RequestMapping("/api/v1/metodos-pago-sat")
@RequiredArgsConstructor
public class MetodoPagoSatController extends AbstractCatalogoController<MetodoPagoSat, String, MetodoPagoSatRequest, MetodoPagoSatResponse> {

    private final MetodoPagoSatService service;

    @Override
    protected AbstractCatalogoService<MetodoPagoSat, String, MetodoPagoSatRequest, MetodoPagoSatResponse> service() { return service; }
}
