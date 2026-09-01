package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FormaPagoResponse;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.FormaPagoService;

@RestController
@RequestMapping("/api/v1/formas-pago")
@RequiredArgsConstructor
public class FormaPagoController extends AbstractCatalogoController<FormaPago, Integer, FormaPagoRequest, FormaPagoResponse> {

    private final FormaPagoService service;

    @Override
    protected AbstractCatalogoService<FormaPago, Integer, FormaPagoRequest, FormaPagoResponse> service() { return service; }
}
