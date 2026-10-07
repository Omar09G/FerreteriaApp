package mx.ferreteria.api.com.service;

/**
 * Dominio compras: se registró una recepción de compra. El módulo de
 * notificaciones lo consume para avisar en tiempo real a
 * GERENTES/ADMINISTRADORES. Vive en com para que compras no dependa del
 * módulo notif (la dependencia va notif → com).
 */
public record CompraCreadaEvent(Long compraId) {
}
