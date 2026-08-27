package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/** Fallo no previsto: loguea stack completo en servidor, devuelve solo correlación. HTTP 500. */
public non-sealed class ErrorInternoException extends ApiException {

    public ErrorInternoException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
