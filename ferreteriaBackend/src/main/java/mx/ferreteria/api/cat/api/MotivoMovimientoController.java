package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MotivoMovimientoRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.MotivoMovimientoResponse;
import mx.ferreteria.api.cat.entity.MotivoMovimiento;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.MotivoMovimientoService;

@RestController
@RequestMapping("/api/v1/motivos-movimiento")
@RequiredArgsConstructor
public class MotivoMovimientoController extends AbstractCatalogoController<MotivoMovimiento, Integer, MotivoMovimientoRequest, MotivoMovimientoResponse> {

    private final MotivoMovimientoService service;

    @Override
    protected AbstractCatalogoService<MotivoMovimiento, Integer, MotivoMovimientoRequest, MotivoMovimientoResponse> service() { return service; }
}
