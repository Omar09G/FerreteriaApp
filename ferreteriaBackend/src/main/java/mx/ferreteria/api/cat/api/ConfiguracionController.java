package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ConfiguracionRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.ConfiguracionResponse;
import mx.ferreteria.api.cat.entity.Configuracion;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.ConfiguracionService;

@RestController
@RequestMapping("/api/v1/configuraciones")
@RequiredArgsConstructor
public class ConfiguracionController extends AbstractCatalogoController<Configuracion, String, ConfiguracionRequest, ConfiguracionResponse> {

    private final ConfiguracionService service;

    @Override
    protected AbstractCatalogoService<Configuracion, String, ConfiguracionRequest, ConfiguracionResponse> service() { return service; }
}
