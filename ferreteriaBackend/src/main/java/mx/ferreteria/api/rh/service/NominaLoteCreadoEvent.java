package mx.ferreteria.api.rh.service;

import java.time.LocalDate;

/**
 * Dominio nómina: se generó una quincena completa (lote). Un solo evento con
 * el conteo para no llenar la bandeja de GERENTES/ADMINISTRADORES con una
 * fila por empleado. Vive en rh (la dependencia va notif → rh).
 */
public record NominaLoteCreadoEvent(int creadas, LocalDate ini, LocalDate fin) {
}
