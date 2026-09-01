package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UsoCfdiRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.UsoCfdiResponse;
import mx.ferreteria.api.cat.entity.UsoCfdi;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.UsoCfdiService;

@RestController
@RequestMapping("/api/v1/usos-cfdi")
@RequiredArgsConstructor
public class UsoCfdiController extends AbstractCatalogoController<UsoCfdi, String, UsoCfdiRequest, UsoCfdiResponse> {

    private final UsoCfdiService service;

    @Override
    protected AbstractCatalogoService<UsoCfdi, String, UsoCfdiRequest, UsoCfdiResponse> service() { return service; }
}
