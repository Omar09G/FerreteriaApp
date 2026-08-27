package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/** page/size/sort fuera de la regla de paginación (PLAN §4.1). HTTP 400. */
public non-sealed class PaginacionInvalidException extends ApiException {

    public PaginacionInvalidException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
