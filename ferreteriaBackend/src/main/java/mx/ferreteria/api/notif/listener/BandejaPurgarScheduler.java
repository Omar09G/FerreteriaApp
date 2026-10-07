package mx.ferreteria.api.notif.listener;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.notif.service.BandejaService;

/**
 * Purga nocturna del historial de la bandeja (retención 90 días). Sin flag:
 * la bandeja en tiempo real funciona aunque los canales externos
 * (app.notif.enabled) estén apagados, y el historial también debe podarse.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BandejaPurgarScheduler {

    private final BandejaService bandejaService;

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
    public void purgarHistorial() {
        try {
            int borradas = bandejaService.purgarAntiguas();
            log.info("bandeja purga ok borradas={}", borradas);
        } catch (Exception e) {
            log.warn("bandeja purga fallo err={}", e.getMessage());
        }
    }
}
