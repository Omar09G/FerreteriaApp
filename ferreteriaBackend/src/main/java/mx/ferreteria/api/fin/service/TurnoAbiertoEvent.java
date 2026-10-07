package mx.ferreteria.api.fin.service;

import java.math.BigDecimal;

/**
 * Dominio caja: se abrió un turno (apertura). El módulo de notificaciones lo
 * consume para avisar en tiempo real a GERENTES/ADMINISTRADORES. Vive en fin
 * para que caja no dependa del módulo notif (la dependencia va notif → fin).
 */
public record TurnoAbiertoEvent(
        Long turnoId,
        Integer cajaId,
        String cajaNombre,
        Integer usuarioId,
        BigDecimal montoApertura) {
}
