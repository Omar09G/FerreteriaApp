package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ClaveProdServRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ClaveProdServResponse;
import mx.ferreteria.api.cat.entity.ClaveProdServ;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.ClaveProdServService;

@RestController
@RequestMapping("/api/v1/claves-prod-serv")
@RequiredArgsConstructor
public class ClaveProdServController extends AbstractCatalogoController<ClaveProdServ, String, ClaveProdServRequest, ClaveProdServResponse> {

    private final ClaveProdServService service;

    @Override
    protected AbstractCatalogoService<ClaveProdServ, String, ClaveProdServRequest, ClaveProdServResponse> service() { return service; }
}
