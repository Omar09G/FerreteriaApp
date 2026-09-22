package mx.ferreteria.api.rh.service;

/**
 * Dominio nómina: una nómina quedó pagada (individual o en lote). El módulo
 * de notificaciones lo consume AFTER_COMMIT para encolar el recibo PDF.
 * Vive en rh para que nómina no dependa del módulo notif (regla
 * arquitectónica modulosSinCiclos: la dependencia va notif → rh).
 */
public record NominaPagadaEvent(Long nominaId) {
}
