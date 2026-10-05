package mx.ferreteria.api.notif.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.StockBajoInformeService;

/**
 * JOB diario del recordatorio de stock bajo a GERENTES y ADMINISTRADORES:
 * todos los días a las 09:15 (zona {@code app.informes.stock-zona}).
 * Requiere notificaciones habilitadas ({@code app.notif.enabled=true}); si el
 * envío falla queda en ERROR en {@code notif.notificacion_jobs} y se
 * reintenta con el botón manual de Inventario → Existencias.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = { "app.notif.enabled", "app.informes.stock-job-enabled" }, havingValue = "true")
public class StockBajoScheduler {

    private final StockBajoInformeService stockService;

    @Scheduled(cron = "${app.informes.stock-cron:0 15 9 * * *}", zone = "${app.informes.stock-zona:America/Mexico_City}")
    public void enviarRecordatorioDiario() {
        try {
            var r = stockService.enviar();
            log.info("stock-bajo automatico ok destinatarios={} emails={} whatsapps={} "
                    + "productos={} agotados={}",
                    r.destinatarios(), r.emailsEnviados(), r.whatsappsEnviados(),
                    r.productos(), r.agotados());
        } catch (Exception e) {
            log.warn("stock-bajo automatico fallo err={}", e.getMessage());
        }
    }
}
