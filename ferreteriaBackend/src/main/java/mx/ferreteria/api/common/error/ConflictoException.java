package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/** Duplicados y carreras concurrentes (unique violado, doble corte simultáneo). HTTP 409. */
public non-sealed class ConflictoException extends ApiException {

    public ConflictoException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
