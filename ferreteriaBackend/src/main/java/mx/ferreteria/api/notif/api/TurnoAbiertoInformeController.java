package mx.ferreteria.api.notif.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RateLimited;
import mx.ferreteria.api.notif.dto.TurnoAbiertoDtos;
import mx.ferreteria.api.notif.service.TurnoAbiertoInformeService;

/**
 * Aviso manual de turnos abiertos (corte sin cerrar) por correo y WhatsApp a
 * GERENTES y ADMINISTRADORES. Mismo contenido del JOB nocturno de las 21:00
 * ({@code app.informes.turno-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/turnos/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class TurnoAbiertoInformeController {

    private final TurnoAbiertoInformeService turnoService;

    /**
     * Estado del aviso de hoy (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    public TurnoAbiertoDtos.TurnoAbiertoEstadoResponse estado() {
        return turnoService.estado();
    }

    /** Envío manual del aviso (bucket "auth" anti-spam). */
    @PostMapping
    @RateLimited("auth")
    public TurnoAbiertoDtos.TurnoAbiertoEnvioResponse enviar() {
        return turnoService.enviar();
    }
}
