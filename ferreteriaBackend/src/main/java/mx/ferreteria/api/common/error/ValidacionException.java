package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/** Entrada inválida (manual o Bean Validation). HTTP 400. */
public non-sealed class ValidacionException extends ApiException {

    public ValidacionException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
