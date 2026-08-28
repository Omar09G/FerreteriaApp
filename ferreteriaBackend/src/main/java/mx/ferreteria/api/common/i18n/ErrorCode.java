package mx.ferreteria.api.common.i18n;

import org.springframework.http.HttpStatus;

/** Única fuente de claves de mensaje. Cada clave existe en ESPAÑOL e INGLÉS. */
public enum ErrorCode {

    // auth
    CREDENCIALES_INVALIDAS("error.auth.credenciales-invalidas", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRADO("error.auth.token-expirado", HttpStatus.UNAUTHORIZED),
    ACCESO_DENEGADO("error.auth.acceso-denegado", HttpStatus.FORBIDDEN),

    // validación / paginación / correlación
    CAMPO_REQUERIDO("error.validacion.campo-requerido", HttpStatus.BAD_REQUEST),
    PAGINACION_INVALIDA("error.validacion.paginacion-invalida", HttpStatus.BAD_REQUEST),
    VALOR_INVALIDO("error.validacion.valor-invalido", HttpStatus.BAD_REQUEST),
    REFERENCIA_INVALIDA("error.validacion.referencia-invalida", HttpStatus.BAD_REQUEST),
    FALTA_REQUEST_ID("error.validacion.falta-request-id", HttpStatus.BAD_REQUEST),
    REQUEST_ID_INVALIDO("error.validacion.request-id-invalido", HttpStatus.BAD_REQUEST),
    IDIOMA_INVALIDO("error.validacion.idioma-invalido", HttpStatus.BAD_REQUEST),
    LIMITE_VELOCIDAD_EXCEDIDO("error.validacion.limite-velocidad-excedido", HttpStatus.TOO_MANY_REQUESTS),

    // negocio (ERRCODE clase P0 en la BD, ver PLAN §4.3)
    STOCK_INSUFICIENTE("error.negocio.stock-insuficiente", HttpStatus.CONFLICT),
    CREDITO_EXCEDIDO("error.negocio.credito-excedido", HttpStatus.UNPROCESSABLE_ENTITY),
    CREDITO_NO_DISPONIBLE("error.negocio.credito-no-disponible", HttpStatus.UNPROCESSABLE_ENTITY),
    TURNO_YA_CERRADO("error.negocio.turno-ya-cerrado", HttpStatus.CONFLICT),
    TURNO_NO_ABIERTO("error.negocio.turno-no-abierto", HttpStatus.CONFLICT),
    CAJA_ALMACEN_INCOMPATIBLE("error.negocio.caja-almacen-incompatible", HttpStatus.CONFLICT),
    FOLIO_DUPLICADO("error.negocio.folio-duplicado", HttpStatus.CONFLICT),
    REGISTRO_DUPLICADO("error.negocio.registro-duplicado", HttpStatus.CONFLICT),
    RECURSO_NO_ENCONTRADO("error.negocio.recurso-no-encontrado", HttpStatus.NOT_FOUND),
    PROMOCION_AGOTADA("error.negocio.promocion-agotada", HttpStatus.CONFLICT),
    PROMOCION_LIMITE_CLIENTE("error.negocio.promocion-limite-cliente", HttpStatus.CONFLICT),
    KARDEX_APPEND_ONLY("error.negocio.kardex-append-only", HttpStatus.CONFLICT),

    // genéricas / internas
    ERROR_INTERNO("error.interno.inesperado", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICIO_NO_DISPONIBLE("error.interno.servicio-no-disponible", HttpStatus.SERVICE_UNAVAILABLE);

    private final String key;
    private final HttpStatus http;

    ErrorCode(String key, HttpStatus http) {
        this.key = key;
        this.http = http;
    }

    public String key() {
        return key;
    }

    public HttpStatus http() {
        return http;
    }
}
