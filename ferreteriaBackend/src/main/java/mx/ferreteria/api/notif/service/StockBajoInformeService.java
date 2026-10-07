package mx.ferreteria.api.notif.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.inv.dto.InvDtos.InventarioResponse;
import mx.ferreteria.api.inv.service.InventarioService;
import mx.ferreteria.api.inv.service.StockBajoExcel;
import mx.ferreteria.api.notif.dto.StockBajoDtos;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

/**
 * Recordatorio de stock bajo a GERENTES y ADMINISTRADORES: correo con
 * resumen + Excel del detalle y WhatsApp con totales. Misma auditoría diaria
 * que los demás recordatorios (tipo STOCK_BAJO). Solo se notifica si hay
 * productos en bajo stock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockBajoInformeService {

    private final InventarioService inventarioService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final ObjectProvider<EmailNotificacionSender> emailSender;
    private final ObjectProvider<WhatsAppNotificacionSender> whatsappSender;
    private final BandejaService bandejaService;

    @Transactional(readOnly = true)
    public StockBajoDtos.StockBajoEstadoResponse estado() {
        LocalDate hoy = ZonaHoraria.hoy();
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_STOCK_BAJO, hoy.toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new StockBajoDtos.StockBajoEstadoResponse(
                hoy, yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public StockBajoDtos.StockBajoEnvioResponse enviar() {
        LocalDate hoy = ZonaHoraria.hoy();
        List<InventarioResponse> filas = inventarioService.bajoStockCompleto();

        NotificacionJob job = jobService.crearStockBajo(hoy);
        jobService.marcarProcesando(job);
        try {
            if (filas.isEmpty()) {
                jobService.marcarEnviada(job, null);
                log.info("stock-bajo sin registros fecha={}", hoy);
                return new StockBajoDtos.StockBajoEnvioResponse(hoy, 0, 0, 0, 0, 0, 0);
            }
            List<DestinatarioInforme> destinatarios = destinatarioRepo
                    .findGerentesYAdministradores();
            if (destinatarios.stream().noneMatch(d -> d.email() != null && !d.email().isBlank())) {
                throw new ValidacionException(ErrorCode.STOCK_SIN_DESTINATARIOS);
            }
            EmailNotificacionSender email = emailSender.getIfAvailable();
            WhatsAppNotificacionSender whatsapp = whatsappSender.getIfAvailable();
            if (email == null && whatsapp == null) {
                jobService.marcarError(job, "sin canales disponibles");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            byte[] xlsx = email == null ? null : StockBajoExcel.generar(filas);
            String nombreArchivo = "stock-bajo-" + hoy + ".xlsx";
            int agotados = (int) filas.stream().filter(StockBajoInformeService::agotado).count();
            int almacenes = (int) filas.stream()
                    .map(InventarioResponse::almacenNombre).distinct().count();
            int emails = 0;
            int whatsapps = 0;
            String resumen = resumenWhatsApp(filas.size(), agotados, almacenes);
            for (DestinatarioInforme d : destinatarios) {
                if (email != null && d.email() != null && !d.email().isBlank()) {
                    try {
                        email.sendStockBajo(d.email(), hoy, filas.size(), agotados,
                                almacenes, xlsx, nombreArchivo);
                        emails++;
                    } catch (RuntimeException e) {
                        log.warn("stock-bajo email fallo to={} err={}", d.email(), e.getMessage());
                    }
                }
                if (whatsapp != null && d.whatsapp() != null && !d.whatsapp().isBlank()) {
                    try {
                        if (whatsapp.sendTexto(d.whatsapp(), resumen)) {
                            whatsapps++;
                        }
                    } catch (RuntimeException e) {
                        log.warn("stock-bajo whatsapp fallo err={}", e.getMessage());
                    }
                }
            }
            if (emails == 0 && whatsapps == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, null);
            bandejaService.publicarParaGerencia(NotificacionBandeja.TIPO_STOCK_BAJO,
                    NotificacionBandeja.REF_STOCK, hoy.toEpochDay(),
                    "Stock bajo: " + filas.size() + " productos (" + agotados + " agotados)",
                    resumenWhatsApp(filas.size(), agotados, almacenes));
            log.info("stock-bajo enviado fecha={} destinatarios={} emails={} whatsapps={} "
                    + "productos={} agotados={}",
                    hoy, destinatarios.size(), emails, whatsapps, filas.size(), agotados);
            return new StockBajoDtos.StockBajoEnvioResponse(hoy, destinatarios.size(),
                    emails, whatsapps, filas.size(), agotados, almacenes);
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }

    private static boolean agotado(InventarioResponse f) {
        return f.stock() != null && f.stock().signum() <= 0;
    }

    static String resumenWhatsApp(int productos, int agotados, int almacenes) {
        return "El Tornillo Feliz — Stock bajo: " + productos + " productos ("
                + agotados + " agotados) en " + almacenes
                + " almacenes. Detalle en su correo. Revise Inventario → Existencias.";
    }
}
