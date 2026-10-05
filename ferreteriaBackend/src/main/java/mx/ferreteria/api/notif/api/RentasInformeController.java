package mx.ferreteria.api.notif.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.notif.dto.RentasDtos;
import mx.ferreteria.api.notif.service.RentasInformeService;

/**
 * Recordatorio manual de rentas (vencidas + próximas a devolver) por correo
 * y WhatsApp a GERENTES y ADMINISTRADORES. Mismo contenido del JOB diario de
 * las 09:10 ({@code app.informes.rentas-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/rentas/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class RentasInformeController {

    private final RentasInformeService rentasService;

    /**
     * Estado del recordatorio de hoy (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public RentasDtos.RentasEstadoResponse estado() {
        return rentasService.estado();
    }

    /** Envío manual del recordatorio (bucket "auth" anti-spam). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @RateLimited("auth")
    public RentasDtos.RentasEnvioResponse enviar() {
        return rentasService.enviar();
    }
}
