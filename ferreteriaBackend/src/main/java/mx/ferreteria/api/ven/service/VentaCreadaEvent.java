package mx.ferreteria.api.ven.service;

/**
 * Dominio ventas: una venta quedó commiteada en checkout. El módulo de
 * notificaciones lo consume AFTER_COMMIT para encolar el ticket PDF.
 * Vive en ven para que ventas no dependa del módulo notif (regla
 * arquitectónica modulosSinCiclos: la dependencia va notif → ven).
 */
public record VentaCreadaEvent(Long ventaId) {
}
