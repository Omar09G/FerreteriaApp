package mx.ferreteria.api.common.error;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import mx.ferreteria.api.common.web.LocaleResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;

/**
 * Único handler global (PLAN §4.6). Errores como Map con envelope
 * {success:false, errorCode, código, errorMessage, requestId, instance?, details?}.
 * EnvelopeAdvice no re-envuelve Mapas que ya tienen "success".
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String REQUEST_ID = "requestId";

    private final MessageSource messages;
    private final DbErrorTranslator dbTranslator;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
        ErrorCode code = ex.errorCode();
        if (code.http().is5xxServerError()) {
            log.error("Error de negocio 5xx codigo={} metodo={} path={} usuario={}",
                    code, metodo(req), path(req), usuario(), ex);
        } else {
            log.warn("Error de negocio codigo={} metodo={} path={} usuario={}",
                    code, metodo(req), path(req), usuario());
        }
        return ResponseEntity.status(code.http())
                .body(errorBody(code, ex.args(), currentLocale(req), req));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex,
                                                               HttpServletRequest req) {
        return dbTranslator.translate(ex)
                .map(code -> {
                    log.warn("DB error traducido a código de negocio: {} metodo={} path={} usuario={}",
                            code, metodo(req), path(req), usuario());
                    return ResponseEntity.status(code.http())
                            .<Map<String, Object>>body(errorBody(code, new Object[0], currentLocale(req), req));
                })
                .orElseGet(() -> {
                    // BACK-SEC-012: el mensaje crudo de PostgreSQL puede incluir esquema,
                    log.error("DataAccessException sin contrato metodo={} path={} usuario={}",
                            metodo(req), path(req), usuario(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .<Map<String, Object>>body(errorBody(ErrorCode.ERROR_INTERNO, requestIdArg(), currentLocale(req), req));
                });
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest req) {
        Map<String, Object> body = errorBody(ErrorCode.CAMPO_REQUERIDO, new Object[0], currentLocale(req), req);
        List<Map<String, String>> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> d = new LinkedHashMap<>();
            d.put("field", fe.getField());
            d.put("error", fe.getDefaultMessage());
            details.add(d);
        }
        body.put("details", details);
        log.warn("Validación fallida campos={} metodo={} path={} usuario={}",
                ex.getBindingResult().getFieldErrors().size(), metodo(req), path(req), usuario());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Ruta no definida (p. ej. GET /): Spring lanza NoResourceFoundException
     * (o NoHandlerFoundException según configuración). Antes caía en
     * handleUnexpected → 500 ERROR_INTERNO + log ERROR con stack por cada
     * probe. Contrato: 403 ACCESO_DENEGADO + log WARN sin stack (causa cliente,
     * no falla interna).
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(Exception ex, HttpServletRequest req) {
        log.warn("Ruta no definida metodo={} path={} usuario={}", metodo(req), path(req), usuario());
        return ResponseEntity.status(ErrorCode.ACCESO_DENEGADO.http())
                .body(errorBody(ErrorCode.ACCESO_DENEGADO, new Object[0], currentLocale(req), req));
    }

    /**
     * @PreAuthorize denegado (Spring Security 6: AuthorizationDeniedException).
     * Antes caía en handleUnexpected → 500 ERROR_INTERNO + log ERROR con stack.
     * Contrato: 403 ACCESO_DENEGADO + log WARN sin stack (causa cliente: rol
     * insuficiente, no falla interna).
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AuthorizationDeniedException ex,
                                                            HttpServletRequest req) {
        log.warn("Acceso denegado por rol metodo={} path={} usuario={}",
                metodo(req), path(req), usuario());
        return ResponseEntity.status(ErrorCode.ACCESO_DENEGADO.http())
                .body(errorBody(ErrorCode.ACCESO_DENEGADO, new Object[0], currentLocale(req), req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado metodo={} path={} usuario={}",
                metodo(req), path(req), usuario(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .<Map<String, Object>>body(errorBody(ErrorCode.ERROR_INTERNO, requestIdArg(), currentLocale(req), req));
    }

    /** Construcción central: {success, data, errorCode, codigo, errorMessage, requestId, instance} */
    Map<String, Object> errorBody(ErrorCode code, Object[] args, Locale locale, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", messages.getMessage(code.key(), args, code.name(), locale));
        String rid = org.slf4j.MDC.get(REQUEST_ID);
        if (rid != null) {
            body.put(REQUEST_ID, rid);
        }
        if (req != null && req.getRequestURI() != null) {
            body.put("instance", req.getRequestURI());
        }
        return body;
    }

    /** Método HTTP nulo-seguro (los tests invocan handlers con request mockeado). */
    private static String metodo(HttpServletRequest req) {
        return req == null || req.getMethod() == null ? "?" : req.getMethod();
    }

    /** Path nulo-seguro. */
    private static String path(HttpServletRequest req) {
        return req == null || req.getRequestURI() == null ? "?" : req.getRequestURI();
    }

    /**
     * Quién provocó el error, sin PII (solo id + username; nunca email ni
     * tokens). "anonimo" si no hay sesión (el filtro de auth ya respondió).
     */
    private static String usuario() {
        try {
            UserPrincipal up = UserPrincipal.actual();
            if (up == null || up.usuarioId() == 0) {
                return "anonimo";
            }
            return "u" + up.usuarioId() + ":" + up.username();
        } catch (Exception e) {
            return "desconocido";
        }
    }

    private Locale currentLocale(jakarta.servlet.http.HttpServletRequest req) {
        if (req != null) {
            Object cached = req.getAttribute(LocaleResolver.ATTR_LOCALE);
            if (cached instanceof Locale l) {
                return l;
            }
        }
        return LocaleContextHolder.getLocale();
    }

    private Object[] requestIdArg() {
        String rid = org.slf4j.MDC.get(REQUEST_ID);
        return rid == null ? new Object[0] : new Object[] {rid};
    }
}
