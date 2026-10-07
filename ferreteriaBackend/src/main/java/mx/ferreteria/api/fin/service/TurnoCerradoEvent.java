package mx.ferreteria.api.fin.service;

import java.math.BigDecimal;

/**
 * Dominio caja: se cerró un turno (corte registrado). El módulo de
 * notificaciones lo consume para avisar en tiempo real a
 * GERENTES/ADMINISTRADORES, destacando si no cuadró. Vive en fin para que
 * caja no dependa del módulo notif (la dependencia va notif → fin).
 */
public record TurnoCerradoEvent(
        Long turnoId,
        String cajaNombre,
        String resultado,
        BigDecimal diferencia,
        Integer usuarioAperturaId,
        Integer usuarioCierreId) {
}
