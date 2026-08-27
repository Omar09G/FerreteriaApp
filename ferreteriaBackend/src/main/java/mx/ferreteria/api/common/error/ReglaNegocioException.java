package mx.ferreteria.api.common.error;

import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Violación de regla de negocio. Incluye TODAS las traducidas desde
 * triggers/funciones de la BD vía ERRCODE clase P0 (PLAN §4.3).
 * HTTP según el ErrorCode (409 o 422).
 */
public non-sealed class ReglaNegocioException extends ApiException {

    public ReglaNegocioException(ErrorCode code, Object... args) {
        super(code, args);
    }
}
