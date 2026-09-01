package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoSatRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoSatResponse;
import mx.ferreteria.api.cat.entity.FormaPagoSat;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.FormaPagoSatService;

@RestController
@RequestMapping("/api/v1/formas-pago-sat")
@RequiredArgsConstructor
public class FormaPagoSatController extends AbstractCatalogoController<FormaPagoSat, String, FormaPagoSatRequest, FormaPagoSatResponse> {

    private final FormaPagoSatService service;

    @Override
    protected AbstractCatalogoService<FormaPagoSat, String, FormaPagoSatRequest, FormaPagoSatResponse> service() { return service; }
}
