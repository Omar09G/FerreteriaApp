package mx.ferreteria.api.ven.api;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionRequest;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionResponse;
import mx.ferreteria.api.ven.service.PromocionService;

/**
 * CRUD de promociones.
 *
 * <p>Lecturas: cualquier usuario autenticado (las necesita POS para aplicar
 * descuentos). Escrituras: ADMINISTRADOR o GERENTE; el rol del JWT viaja en
 * el SecurityContext (JwtAuthFilter → roles authorities ROLE_*).
 */
@RestController
@RequestMapping("/api/v1/promociones")
@RequiredArgsConstructor
public class PromocionController {

    private final PromocionService service;

    @GetMapping
    public Page<PromocionResponse> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = PageQuery.of(page, size, sort).toPageable();
        return service.listar(nombre, tipo, estado, desde, hasta, pageable);
    }

    @GetMapping("/{id}")
    public PromocionResponse obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public PromocionResponse crear(@Valid @RequestBody PromocionRequest req) {
        return service.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public PromocionResponse actualizar(@PathVariable long id, @Valid @RequestBody PromocionRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public void eliminar(@PathVariable long id) {
        service.eliminar(id);
    }
}