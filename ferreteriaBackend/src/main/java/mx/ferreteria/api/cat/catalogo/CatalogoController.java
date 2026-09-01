package mx.ferreteria.api.cat.catalogo;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Metadata de catálogos de administración (PLAN catalogo). Expone los
 * descriptores (paneles) que el front usa para renderizar cada tabla y las
 * opciones de los campos FK. El CRUD por tabla vive en los endpoints
 * individuales (api/EstadoController, etc.).
 *
 * Seguridad: lectura abierta a cualquier autenticado (POS/ventas).
 */
@RestController
@RequestMapping("/api/v1/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService service;

    @GetMapping
    public List<Catalogo> paneles() {
        return service.paneles();
    }

    @GetMapping("/{clave}")
    public Catalogo porClave(@PathVariable String clave) {
        return service.porClave(clave);
    }

    @GetMapping("/{clave}/opciones")
    public List<OpcionesCatalogoService.OpcionFk> opciones(
            @PathVariable String clave,
            @RequestParam String campo) {
        return service.opciones(clave, campo);
    }
}
