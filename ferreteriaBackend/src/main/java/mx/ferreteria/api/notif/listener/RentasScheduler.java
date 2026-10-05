package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.RentasInformeService;

/**
 * JOB diario del recordatorio de rentas (vencidas + próximas a devolver) a
 * GERENTES y ADMINISTRADORES: todos los días a las 09:10
 * (zona {@code app.informes.rentas-zona}). Requiere notificaciones
 * habilitadas ({@code app.notif.enabled=true}); si el envío falla queda en
 * ERROR en {@code notif.notificacion_jobs} y se reintenta con el botón manual
 * de Ventas → Rentas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.rentas-job-enabled" }, havingValue = "true")
public class RentasScheduler {

    private final RentasInformeService rentasService;

    @Scheduled(cron = "${app.informes.rentas-cron:0 10 9 * * *}", zone = "${app.informes.rentas-zona:America/Mexico_City}")
    public void enviarRecordatorioDiario() {
        try {
            var r = rentasService.enviar();
            log.info("rentas automaticas ok destinatarios={} emails={} whatsapps={} "
                    + "vencidas={} proximas={}",
                    r.destinatarios(), r.emailsEnviados(), r.whatsappsEnviados(),
                    r.vencidas(), r.proximas());
        } catch (Exception e) {
            log.warn("rentas automaticas fallo err={}", e.getMessage());
        }
    }
}
