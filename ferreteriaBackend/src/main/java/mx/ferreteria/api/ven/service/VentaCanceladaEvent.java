package mx.ferreteria.api.ven.service;

/**
 * Dominio ventas: una venta fue cancelada. El módulo de notificaciones lo
 * consume para avisar en tiempo real a GERENTES/ADMINISTRADORES. Vive en ven
 * para que ventas no dependa del módulo notif (la dependencia va notif →
 * ven).
 */
public record VentaCanceladaEvent(Long ventaId) {
}
