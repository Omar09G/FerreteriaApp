package mx.ferreteria.api.rh.service;

/**
 * Dominio nómina: se creó una nómina (pendiente de pago). El módulo de
 * notificaciones lo consume para avisar en tiempo real a
 * GERENTES/ADMINISTRADORES. Vive en rh para que nómina no dependa del módulo
 * notif (la dependencia va notif → rh).
 */
public record NominaCreadaEvent(Long nominaId) {
}
