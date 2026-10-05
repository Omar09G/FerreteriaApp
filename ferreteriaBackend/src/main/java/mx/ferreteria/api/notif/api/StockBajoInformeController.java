package mx.ferreteria.api.notif.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.notif.dto.StockBajoDtos;
import mx.ferreteria.api.notif.service.StockBajoInformeService;

/**
 * Recordatorio manual de stock bajo (resumen + Excel del detalle) por correo
 * y WhatsApp a GERENTES y ADMINISTRADORES. Mismo contenido del JOB diario de
 * las 09:15 ({@code app.informes.stock-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/stock-bajo/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class StockBajoInformeController {

    private final StockBajoInformeService stockService;

    /**
     * Estado del recordatorio de hoy (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public StockBajoDtos.StockBajoEstadoResponse estado() {
        return stockService.estado();
    }

    /** Envío manual del recordatorio (bucket "auth" anti-spam). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @RateLimited("auth")
    public StockBajoDtos.StockBajoEnvioResponse enviar() {
        return stockService.enviar();
    }
}
