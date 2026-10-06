package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.TurnoAbiertoInformeService;

/**
 * JOB nocturno del aviso de turnos abiertos (corte sin cerrar) a GERENTES y
 * ADMINISTRADORES: todos los días a las 21:00
 * (zona {@code app.informes.turno-zona}). Requiere notificaciones
 * habilitadas ({@code app.notif.enabled=true}); si el envío falla queda en
 * ERROR en {@code notif.notificacion_jobs} y se reintenta con el botón manual
 * de Caja.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.turno-job-enabled" }, havingValue = "true")
public class TurnoAbiertoScheduler {

    private final TurnoAbiertoInformeService turnoService;

    @Scheduled(cron = "${app.informes.turno-cron:0 0 21 * * *}", zone = "${app.informes.turno-zona:America/Mexico_City}")
    public void enviarAvisoNocturno() {
        try {
            var r = turnoService.enviar();
            log.info("turnos automaticos ok destinatarios={} emails={} whatsapps={} turnos={}",
                    r.destinatarios(), r.emailsEnviados(), r.whatsappsEnviados(), r.turnos());
        } catch (Exception e) {
            log.warn("turnos automaticos fallo err={}", e.getMessage());
        }
    }
}
