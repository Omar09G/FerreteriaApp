package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.RegimenFiscalRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.RegimenFiscalResponse;
import mx.ferreteria.api.cat.entity.RegimenFiscal;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.RegimenFiscalService;

@RestController
@RequestMapping("/api/v1/regimenes-fiscales")
@RequiredArgsConstructor
public class RegimenFiscalController extends AbstractCatalogoController<RegimenFiscal, String, RegimenFiscalRequest, RegimenFiscalResponse> {

    private final RegimenFiscalService service;

    @Override
    protected AbstractCatalogoService<RegimenFiscal, String, RegimenFiscalRequest, RegimenFiscalResponse> service() { return service; }
}
