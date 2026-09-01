package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FolioRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.FolioResponse;
import mx.ferreteria.api.cat.entity.Folio;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.FolioService;

@RestController
@RequestMapping("/api/v1/folios")
@RequiredArgsConstructor
public class FolioController extends AbstractCatalogoController<Folio, String, FolioRequest, FolioResponse> {

    private final FolioService service;

    @Override
    protected AbstractCatalogoService<Folio, String, FolioRequest, FolioResponse> service() { return service; }
}
