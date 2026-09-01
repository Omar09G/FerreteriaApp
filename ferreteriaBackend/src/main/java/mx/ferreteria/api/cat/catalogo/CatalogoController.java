package mx.ferreteria.api.cat.catalogo;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * CRUD genérico de catálogos de administración (PLAN catalogo). Cada catálogo
 * se define en {@link Catalogos}; aquí no hay lógica por tabla.
 *
 * Seguridad: lectura (GET) abierta a cualquier autenticado (POS/ventas lo
 * necesitan); escritura (POST/PUT/DELETE) exclusiva de ADMINISTRADOR.
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

    @GetMapping("/{clave}/datos")
    public org.springframework.data.domain.Page<Map<String, Object>> datos(
            @PathVariable String clave,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return service.datos(clave, q, page, size, sort);
    }

    @GetMapping("/{clave}/opciones")
    public List<Map<String, Object>> opciones(
            @PathVariable String clave,
            @RequestParam String campo,
            @RequestParam(required = false) String q) {
        return service.opciones(clave, campo, q);
    }

    @PostMapping("/{clave}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void crear(@PathVariable String clave, @RequestBody Map<String, Object> cuerpo) {
        service.crear(clave, cuerpo);
    }

    @PutMapping("/{clave}/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void actualizar(@PathVariable String clave, @PathVariable String id,
                           @RequestBody Map<String, Object> cuerpo) {
        service.actualizar(clave, id, cuerpo);
    }

    @DeleteMapping("/{clave}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void eliminar(@PathVariable String clave, @PathVariable String id) {
        service.eliminar(clave, id);
    }
}
