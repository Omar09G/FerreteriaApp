package mx.ferreteria.api.notif.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse;
import mx.ferreteria.api.com.service.CompraService;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.notif.dto.CuentasPagarDtos;
import mx.ferreteria.api.notif.entity.NotificacionJob;
import mx.ferreteria.api.notif.repo.NotificacionJobRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository.DestinatarioInforme;

/**
 * Recordatorio de cuentas por pagar (vencidas + pendientes) a GERENTES y
 * ADMINISTRADORES con correo. Mismo esquema que el informe diario: un job
 * por día en {@code notif.notificacion_jobs} (tipo CUENTAS_PAGAR, ref_id =
 * epoch day) para avisar si ya se envió y permitir el reenvío manual.
 * Solo correo (sin PDF ni WhatsApp): el detalle vive en el HTML.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CuentasPagarInformeService {

    private final CompraService compraService;
    private final InformeDestinatarioRepository destinatarioRepo;
    private final NotificacionJobService jobService;
    private final NotificacionJobRepository jobRepo;
    private final ObjectProvider<EmailNotificacionSender> emailSender;

    @Transactional(readOnly = true)
    public CuentasPagarDtos.CuentasPagarEstadoResponse estado() {
        LocalDate hoy = ZonaHoraria.hoy();
        var job = jobRepo
                .findByTipoAndRefId(NotificacionJob.TIPO_CUENTAS_PAGAR, hoy.toEpochDay())
                .orElse(null);
        boolean yaEnviado = job != null && NotificacionJob.ESTADO_ENVIADA.equals(job.getEstado());
        return new CuentasPagarDtos.CuentasPagarEstadoResponse(
                hoy, yaEnviado,
                job != null ? job.getEstado() : null,
                job != null ? job.getEnviadoEn() : null);
    }

    @Transactional
    public CuentasPagarDtos.CuentasPagarEnvioResponse enviar() {
        LocalDate hoy = ZonaHoraria.hoy();
        List<FacturaVencidaResponse> vencidas = compraService.facturasVencidas();
        List<FacturaPendienteResponse> pendientes = compraService.facturasPendientes();

        NotificacionJob job = jobService.crearCuentasPagar(hoy);
        jobService.marcarProcesando(job);
        try {
            if (vencidas.isEmpty() && pendientes.isEmpty()) {
                // Sin adeudos: no hay nada que recordar; se audita y no se envía.
                jobService.marcarEnviada(job, null);
                log.info("cuentas-pagar sin adeudos fecha={}", hoy);
                return new CuentasPagarDtos.CuentasPagarEnvioResponse(hoy, 0, 0, 0, 0,
                        BigDecimal.ZERO, BigDecimal.ZERO);
            }
            List<DestinatarioInforme> destinatarios = destinatarioRepo
                    .findGerentesYAdministradores().stream()
                    .filter(d -> d.email() != null && !d.email().isBlank())
                    .toList();
            if (destinatarios.isEmpty()) {
                throw new ValidacionException(ErrorCode.CUENTAS_SIN_DESTINATARIOS);
            }
            EmailNotificacionSender email = emailSender.getIfAvailable();
            if (email == null) {
                jobService.marcarError(job, "canal email no disponible");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            int emails = 0;
            for (DestinatarioInforme d : destinatarios) {
                try {
                    email.sendCuentasPagar(d.email(), hoy, vencidas, pendientes);
                    emails++;
                } catch (RuntimeException e) {
                    log.warn("cuentas-pagar email fallo to={} err={}", d.email(), e.getMessage());
                }
            }
            if (emails == 0) {
                jobService.marcarError(job, "sin entregas");
                throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
            }
            jobService.marcarEnviada(job, null);
            log.info("cuentas-pagar enviado fecha={} destinatarios={} emails={} "
                    + "vencidas={} pendientes={}",
                    hoy, destinatarios.size(), emails, vencidas.size(), pendientes.size());
            return new CuentasPagarDtos.CuentasPagarEnvioResponse(hoy, destinatarios.size(),
                    emails, vencidas.size(), pendientes.size(),
                    total(vencidas.stream().map(FacturaVencidaResponse::saldo).toList()),
                    total(pendientes.stream().map(FacturaPendienteResponse::saldo).toList()));
        } catch (RuntimeException e) {
            if (NotificacionJob.ESTADO_PROCESANDO.equals(job.getEstado())) {
                jobService.marcarError(job, e.getMessage());
            }
            throw e;
        }
    }

    private static BigDecimal total(List<BigDecimal> saldos) {
        return saldos.stream()
                .map(s -> s == null ? BigDecimal.ZERO : s)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
