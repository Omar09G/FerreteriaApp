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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Único handler global (PLAN §4.6). Errores como Map con envelope
 * {success:false, errorCode, codigo, errorMessage, requestId, instance?, details?}.
 * EnvelopeAdvice no re-envuelve Mapas que ya tienen "success".
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messages;
    private final DbErrorTranslator dbTranslator;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.errorCode().http())
                .body(errorBody(ex.errorCode(), ex.args(), currentLocale(), req));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex,
                                                               HttpServletRequest req) {
        return dbTranslator.translate(ex)
                .map(code -> {
                    log.warn("ERRCODE de negocio traducido: {} -> {}", code, ex.getMostSpecificCause().getMessage());
                    return ResponseEntity.status(code.http())
                            .<Map<String, Object>>body(errorBody(code, new Object[0], currentLocale(), req));
                })
                .orElseGet(() -> {
                    log.error("DataAccessException sin contrato", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .<Map<String, Object>>body(errorBody(ErrorCode.ERROR_INTERNO, requestIdArg(), currentLocale(), req));
                });
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest req) {
        Map<String, Object> body = errorBody(ErrorCode.CAMPO_REQUERIDO, new Object[0], currentLocale(), req);
        List<Map<String, String>> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> d = new LinkedHashMap<>();
            d.put("field", fe.getField());
            d.put("error", fe.getDefaultMessage());
            details.add(d);
        }
        body.put("details", details);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .<Map<String, Object>>body(errorBody(ErrorCode.ERROR_INTERNO, requestIdArg(), currentLocale(), req));
    }

    /** Construcción central: {success, data, errorCode, codigo, errorMessage, requestId, instance} */
    Map<String, Object> errorBody(ErrorCode code, Object[] args, Locale locale, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", messages.getMessage(code.key(), args, code.name(), locale));
        String rid = org.slf4j.MDC.get("requestId");
        if (rid != null) {
            body.put("requestId", rid);
        }
        if (req != null && req.getRequestURI() != null) {
            body.put("instance", req.getRequestURI());
        }
        return body;
    }

    private Locale currentLocale() {
        return LocaleContextHolder.getLocale();
    }

    private Object[] requestIdArg() {
        String rid = org.slf4j.MDC.get("requestId");
        return rid == null ? new Object[0] : new Object[] {rid};
    }
}
