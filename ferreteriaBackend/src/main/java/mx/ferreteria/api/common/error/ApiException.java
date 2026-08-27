package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Base de todas las excepciones del API.
 * Regla inviolable: NUNCA se construyen con texto literal — solo ErrorCode + args
 * que el handler interpola vía MessageSource (i18n es/en).
 */
public abstract sealed class ApiException extends RuntimeException
        permits ValidacionException, PaginacionInvalidException, RecursoNoEncontradoException,
                ReglaNegocioException, ConflictoException, ErrorInternoException {

    private final transient ErrorCode errorCode;
    private final transient Object[] args;

    protected ApiException(ErrorCode errorCode, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] args() {
        return args == null ? new Object[0] : args.clone();
    }
}
