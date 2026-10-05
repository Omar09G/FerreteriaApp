package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.CobranzaInformeService;

/**
 * JOB diario del recordatorio de cobranza (vencidas + pendientes) a GERENTES
 * y ADMINISTRADORES: todos los días a las 09:05
 * (zona {@code app.informes.cobranza-zona}). Requiere notificaciones
 * habilitadas ({@code app.notif.enabled=true}); si el envío falla queda en
 * ERROR en {@code notif.notificacion_jobs} y se reintenta con el botón manual
 * de Ventas → Cobranza.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.cobranza-job-enabled" }, havingValue = "true")
public class CobranzaScheduler {

    private final CobranzaInformeService cobranzaService;

    @Scheduled(cron = "${app.informes.cobranza-cron:0 5 9 * * *}", zone = "${app.informes.cobranza-zona:America/Mexico_City}")
    public void enviarRecordatorioDiario() {
        try {
            var r = cobranzaService.enviar();
            log.info("cobranza automatica ok destinatarios={} emails={} whatsapps={} "
                    + "vencidas={} pendientes={}",
                    r.destinatarios(), r.emailsEnviados(), r.whatsappsEnviados(),
                    r.vencidas(), r.pendientes());
        } catch (Exception e) {
            log.warn("cobranza automatica fallo err={}", e.getMessage());
        }
    }
}
