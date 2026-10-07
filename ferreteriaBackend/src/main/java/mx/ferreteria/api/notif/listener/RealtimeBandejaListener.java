package mx.ferreteria.api.notif.listener;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.com.repo.CompraRepository;
import mx.ferreteria.api.com.service.CompraCreadaEvent;
import mx.ferreteria.api.fin.service.TurnoAbiertoEvent;
import mx.ferreteria.api.fin.service.TurnoCerradoEvent;
import mx.ferreteria.api.notif.entity.NotificacionBandeja;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.notif.service.EmailNotificacionSender;
import mx.ferreteria.api.rh.repo.NominaRepository;
import mx.ferreteria.api.rh.service.NominaCreadaEvent;
import mx.ferreteria.api.rh.service.NominaLoteCreadoEvent;
import mx.ferreteria.api.rh.service.NominaPagadaEvent;
import mx.ferreteria.api.seg.repo.InformeDestinatarioRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;
import mx.ferreteria.api.ven.service.VentaCanceladaEvent;
import mx.ferreteria.api.ven.service.VentaCreadaEvent;

/**
 * Hook de tiempo real: convierte eventos de dominio en filas de bandeja y
 * push SSE. Corre AFTER_COMMIT con {@code fallbackExecution = true} (los
 * publishers sin transacción propia, como crear nómina, ya commitearon con
 * el flush) y en REQUIRES_NEW para no acoplarse al destino.
 * <p>
 * No toca el pipeline de PDF/correo ({@link NotificacionCreacionListener}):
 * la bandeja vive aunque los canales externos fallen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeBandejaListener {

    private final BandejaService bandeja;
    private final InformeDestinatarioRepository destinatarios;
    private final VentaRepository ventaRepo;
    private final CompraRepository compraRepo;
    private final NominaRepository nominaRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVentaCreada(VentaCreadaEvent event) {
        try {
            var v = ventaRepo.findById(event.ventaId()).orElse(null);
            if (v == null) {
                return;
            }
            bandeja.publicar(NotificacionBandeja.TIPO_VENTA_TICKET,
                    NotificacionBandeja.REF_VENTA, v.getVentaId(),
                    "Venta " + v.getFolio() + " registrada",
                    "Total " + EmailNotificacionSender.moneda(v.getTotal()),
                    conGerencia(v.getUsuarioId()));
        } catch (Exception e) {
            log.warn("bandeja venta falló venta_id={} err={}", event.ventaId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVentaCancelada(VentaCanceladaEvent event) {
        try {
            var v = ventaRepo.findById(event.ventaId()).orElse(null);
            if (v == null) {
                return;
            }
            bandeja.publicar(NotificacionBandeja.TIPO_VENTA_CANCELADA,
                    NotificacionBandeja.REF_VENTA, v.getVentaId(),
                    "Venta " + v.getFolio() + " cancelada",
                    "Total " + EmailNotificacionSender.moneda(v.getTotal()),
                    conGerencia(v.getUsuarioId()));
        } catch (Exception e) {
            log.warn("bandeja venta-cancelada falló venta_id={} err={}",
                    event.ventaId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompraCreada(CompraCreadaEvent event) {
        try {
            var c = compraRepo.findById(event.compraId()).orElse(null);
            if (c == null) {
                return;
            }
            bandeja.publicar(NotificacionBandeja.TIPO_COMPRA_CREADA,
                    NotificacionBandeja.REF_COMPRA, c.getCompraId(),
                    "Compra " + c.getFolio() + " recibida",
                    "Total " + EmailNotificacionSender.moneda(c.getTotal()),
                    conGerencia(c.getUsuarioId()));
        } catch (Exception e) {
            log.warn("bandeja compra falló compra_id={} err={}", event.compraId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTurnoAbierto(TurnoAbiertoEvent event) {
        try {
            bandeja.publicar(NotificacionBandeja.TIPO_TURNO_APERTURA,
                    NotificacionBandeja.REF_TURNO, event.turnoId(),
                    "Turno abierto en " + event.cajaNombre(),
                    "Apertura con " + EmailNotificacionSender.moneda(event.montoApertura()),
                    conGerencia(event.usuarioId()));
        } catch (Exception e) {
            log.warn("bandeja turno-abierto falló turno_id={} err={}",
                    event.turnoId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTurnoCerrado(TurnoCerradoEvent event) {
        try {
            boolean cuadrado = "CUADRADO".equals(event.resultado());
            bandeja.publicar(NotificacionBandeja.TIPO_CORTE_CAJA,
                    NotificacionBandeja.REF_TURNO, event.turnoId(),
                    "Corte en " + event.cajaNombre() + ": " + (cuadrado ? "cuadró"
                            : event.resultado().toLowerCase()),
                    cuadrado ? "Sin diferencias"
                            : "Diferencia " + EmailNotificacionSender.moneda(event.diferencia()),
                    conGerencia(event.usuarioAperturaId(), event.usuarioCierreId()));
        } catch (Exception e) {
            log.warn("bandeja turno-cerrado falló turno_id={} err={}",
                    event.turnoId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNominaCreada(NominaCreadaEvent event) {
        try {
            var n = nominaRepo.findById(event.nominaId()).orElse(null);
            if (n == null) {
                return;
            }
            bandeja.publicarParaGerencia(NotificacionBandeja.TIPO_NOMINA_CREADA,
                    NotificacionBandeja.REF_NOMINA, n.getNominaId(),
                    "Nómina creada (pendiente de pago)",
                    "Periodo " + n.getPeriodoIni() + " al " + n.getPeriodoFin());
        } catch (Exception e) {
            log.warn("bandeja nomina-creada falló nomina_id={} err={}",
                    event.nominaId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNominaLoteCreado(NominaLoteCreadoEvent event) {
        try {
            bandeja.publicarParaGerencia(NotificacionBandeja.TIPO_NOMINA_CREADA,
                    NotificacionBandeja.REF_NOMINA, event.ini().toEpochDay(),
                    "Quincena generada: " + event.creadas() + " nóminas",
                    "Periodo " + event.ini() + " al " + event.fin());
        } catch (Exception e) {
            log.warn("bandeja nomina-lote falló err={}", e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNominaPagada(NominaPagadaEvent event) {
        try {
            var n = nominaRepo.findById(event.nominaId()).orElse(null);
            if (n == null) {
                return;
            }
            List<Integer> dest = conGerencia(
                    destinatarios.findUsuarioIdByEmpleadoId(n.getEmpleadoId()).orElse(null));
            bandeja.publicar(NotificacionBandeja.TIPO_NOMINA_PAGADA,
                    NotificacionBandeja.REF_NOMINA, n.getNominaId(),
                    "Nómina pagada",
                    "Neto " + EmailNotificacionSender.moneda(n.getNetoPagar()),
                    dest);
        } catch (Exception e) {
            log.warn("bandeja nomina-pagada falló nomina_id={} err={}",
                    event.nominaId(), e.getMessage());
        }
    }

    /** Gerentes/admins + usuarios propios involucrados (sin nulos ni ceros). */
    private List<Integer> conGerencia(Integer... propios) {
        List<Integer> dest = new ArrayList<>(destinatarios.findGerenteAdminIds());
        for (Integer id : propios) {
            if (id != null && id != 0 && !dest.contains(id)) {
                dest.add(id);
            }
        }
        return dest;
    }
}
