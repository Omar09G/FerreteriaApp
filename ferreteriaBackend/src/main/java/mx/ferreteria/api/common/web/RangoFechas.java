package mx.ferreteria.api.common.web;

import java.time.LocalDate;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Rango de fechas para consultas por periodo de reportes, dashboard,
 * inventario (movimientos) y demás lecturas diarias. Default: el día actual
 * (fechaInicio = fechaFin = hoy). fechaInicio después de fechaFin -> 400
 * VALOR_INVALIDO. El acotar por fechas empuja los filtros a índices del día
 * (performance) y permite consultar valores que cambian día a día.
 */
public record RangoFechas(LocalDate inicio, LocalDate fin) {

    public static RangoFechas of(LocalDate inicio, LocalDate fin) {
        LocalDate i = inicio != null ? inicio : LocalDate.now();
        LocalDate f = fin != null ? fin : i;
        if (f.isBefore(i)) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        return new RangoFechas(i, f);
    }
}