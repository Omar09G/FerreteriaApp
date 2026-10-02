package mx.ferreteria.api.notif.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RangoFechas;
import mx.ferreteria.api.notif.dto.InformeDtos;
import mx.ferreteria.api.notif.service.DashboardInformeService;

/**
 * Envío del informe diario del dashboard (KPIs + cierre) por correo y
 * WhatsApp a GERENTES y ADMINISTRADORES con contacto asignado. Mismo PDF
 * del JOB programado ({@code app.informes.dashboard-cron}).
 */
@RestController
@RequestMapping("/api/v1/reportes/dashboard/informe")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class InformeController {

    private final DashboardInformeService informeService;

    /**
     * Estado del informe para el rango (lee notif.notificacion_jobs):
     * permite avisar si ya fue enviado antes de reenviar.
     */
    @GetMapping("/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public InformeDtos.InformeEstadoResponse estado(
            @RequestParam(name = "fechaInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = RangoFechas.of(fechaInicio, fechaFin);
        return informeService.estadoInforme(rango.inicio(), rango.fin());
    }

    /** Envío manual del informe diario. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public InformeDtos.InformeEnvioResponse enviar(
            @RequestParam(name = "fechaInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = RangoFechas.of(fechaInicio, fechaFin);
        return informeService.enviarInforme(rango.inicio(), rango.fin());
    }
}
