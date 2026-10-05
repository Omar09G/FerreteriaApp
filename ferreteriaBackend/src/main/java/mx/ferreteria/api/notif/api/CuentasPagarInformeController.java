package mx.ferreteria.api.notif.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.notif.dto.CuentasPagarDtos;
import mx.ferreteria.api.notif.service.CuentasPagarInformeService;

/**
 * Recordatorio manual de cuentas por pagar (vencidas + pendientes) por
 * correo a GERENTES y ADMINISTRADORES. Mismo contenido del JOB diario de
 * las 09:00 ({@code app.informes.cuentas-pagar-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/cuentas-pagar/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class CuentasPagarInformeController {

    private final CuentasPagarInformeService cuentasService;

    /**
     * Estado del recordatorio de hoy (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public CuentasPagarDtos.CuentasPagarEstadoResponse estado() {
        return cuentasService.estado();
    }

    /** Envío manual del recordatorio (bucket "auth" anti-spam). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @RateLimited("auth")
    public CuentasPagarDtos.CuentasPagarEnvioResponse enviar() {
        return cuentasService.enviar();
    }
}
