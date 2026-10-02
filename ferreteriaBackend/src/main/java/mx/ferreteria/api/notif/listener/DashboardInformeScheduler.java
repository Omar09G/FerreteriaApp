package mx.ferreteria.api.notif.listener;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.notif.service.DashboardInformeService;

/**
 * JOB diario del informe del dashboard (KPIs + cierre): un envío al día a la
 * hora de {@code app.informes.dashboard-cron} (zona
 * {@code app.informes.dashboard-zona}). Requiere notificaciones habilitadas
 * ({@code app.notif.enabled=true}); si el envío falla queda en ERROR en
 * {@code notif.notificacion_jobs} y se reintenta con el botón manual.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.dashboard-job-enabled" }, havingValue = "true")
public class DashboardInformeScheduler {

    private final DashboardInformeService informeService;

    @Scheduled(cron = "${app.informes.dashboard-cron:0 0 7 * * *}", zone = "${app.informes.dashboard-zona:America/Mexico_City}")
    public void enviarInformeDiario() {
        LocalDate hoy = ZonaHoraria.hoy();
        try {
            var r = informeService.enviarInforme(hoy, hoy);
            log.info("informe diario automatico ok destinatarios={} emails={} whatsapps={}",
                    r.destinatarios(), r.emailsEnviados(), r.whatsappEnviados());
        } catch (Exception e) {
            log.warn("informe diario automatico fallo err={}", e.getMessage());
        }
    }
}
