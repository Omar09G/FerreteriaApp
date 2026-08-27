package mx.ferreteria.api.ven.api;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.PagoService;

@RestController
@RequestMapping("/api/v1/pagos-cliente")
@RequiredArgsConstructor
@Validated
public class PagoClienteController {

    private final PagoService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenDtos.PagoResponse create(@Valid @RequestBody VenDtos.PagoClienteRequest req) {
        return service.create(req);
    }
}
