package mx.ferreteria.api.cat.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatalogoDtos.CiudadRequest;
import mx.ferreteria.api.cat.dto.CatalogoDtos.CiudadResponse;
import mx.ferreteria.api.cat.entity.Ciudad;
import mx.ferreteria.api.cat.service.AbstractCatalogoService;
import mx.ferreteria.api.cat.service.CiudadService;

@RestController
@RequestMapping("/api/v1/ciudades")
@RequiredArgsConstructor
public class CiudadController extends AbstractCatalogoController<Ciudad, Integer, CiudadRequest, CiudadResponse> {

    private final CiudadService service;

    @Override
    protected AbstractCatalogoService<Ciudad, Integer, CiudadRequest, CiudadResponse> service() { return service; }
}
