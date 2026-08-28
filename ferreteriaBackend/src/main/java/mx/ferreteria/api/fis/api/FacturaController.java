package mx.ferreteria.api.fis.api;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.PageQuery;
import mx.ferreteria.api.fis.dto.FisDtos;
import mx.ferreteria.api.fis.service.FacturaFisService;

@RestController
@RequestMapping("/api/v1/facturas")
@RequiredArgsConstructor
@Validated
public class FacturaController {

    private final FacturaFisService service;

    @GetMapping
    public Page<FisDtos.FacturaFisResponse> list(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return service.list(tipo, desde, hasta, PageQuery.of(page, size, sort).toPageable());
    }

    @GetMapping("/{id}")
    public FisDtos.FacturaFisResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/xml")
    public FisDtos.FacturaXmlResponse getXml(@PathVariable Long id) {
        return service.getXml(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FisDtos.FacturaFisResponse create(@Valid @RequestBody FisDtos.FacturaFisRequest req) {
        return service.create(req);
    }
}