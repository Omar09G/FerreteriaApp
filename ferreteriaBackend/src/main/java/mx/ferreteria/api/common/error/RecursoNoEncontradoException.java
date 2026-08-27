package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/** Id inexistente o turno inexistente. HTTP 404. */
public non-sealed class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
