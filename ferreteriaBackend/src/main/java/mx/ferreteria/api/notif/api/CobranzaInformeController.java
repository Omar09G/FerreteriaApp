package mx.ferreteria.api.notif.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.notif.dto.CobranzaDtos;
import mx.ferreteria.api.notif.service.CobranzaInformeService;

/**
 * Recordatorio manual de cobranza (cuentas vencidas + pendientes) por correo
 * y WhatsApp a GERENTES y ADMINISTRADORES. Mismo contenido del JOB diario de
 * las 09:05 ({@code app.informes.cobranza-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/cobranza/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class CobranzaInformeController {

    private final CobranzaInformeService cobranzaService;

    /**
     * Estado del recordatorio de hoy (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public CobranzaDtos.CobranzaEstadoResponse estado() {
        return cobranzaService.estado();
    }

    /** Envío manual del recordatorio (bucket "auth" anti-spam). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @RateLimited("auth")
    public CobranzaDtos.CobranzaEnvioResponse enviar() {
        return cobranzaService.enviar();
    }
}
