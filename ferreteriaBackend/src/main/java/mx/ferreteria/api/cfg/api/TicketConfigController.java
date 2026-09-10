package mx.ferreteria.api.cfg.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigRequest;
import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigResponse;
import mx.ferreteria.api.cfg.service.TicketConfigService;

@RestController
@RequestMapping("/api/v1/configuracion/ticket")
@RequiredArgsConstructor
public class TicketConfigController {

    private final TicketConfigService service;

    @GetMapping
    public TicketConfigResponse get(@RequestParam(name = "almacenId", required = false) Integer almacenId) {
        return service.get(almacenId);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public TicketConfigResponse upsert(@Valid @RequestBody TicketConfigRequest req) {
        return service.upsert(req);
    }
}
