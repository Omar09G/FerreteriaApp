package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.CuentasPagarInformeService;

/**
 * JOB diario del recordatorio de cuentas por pagar (vencidas + pendientes)
 * a GERENTES y ADMINISTRADORES: todos los días a las 09:00
 * (zona {@code app.informes.cuentas-pagar-zona}). Requiere notificaciones
 * habilitadas ({@code app.notif.enabled=true}); si el envío falla queda en
 * ERROR en {@code notif.notificacion_jobs} y se reintenta con el botón manual
 * de Compras → Cuentas por pagar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.cuentas-pagar-job-enabled" }, havingValue = "true")
public class CuentasPagarScheduler {

    private final CuentasPagarInformeService cuentasService;

    @Scheduled(cron = "${app.informes.cuentas-pagar-cron:0 0 9 * * *}", zone = "${app.informes.cuentas-pagar-zona:America/Mexico_City}")
    public void enviarRecordatorioDiario() {
        try {
            var r = cuentasService.enviar();
            log.info("cuentas-pagar automatico ok destinatarios={} emails={} "
                    + "vencidas={} pendientes={}",
                    r.destinatarios(), r.emailsEnviados(), r.vencidas(), r.pendientes());
        } catch (Exception e) {
            log.warn("cuentas-pagar automatico fallo err={}", e.getMessage());
        }
    }
}
