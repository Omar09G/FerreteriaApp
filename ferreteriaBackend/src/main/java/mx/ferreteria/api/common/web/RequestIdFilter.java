package mx.ferreteria.api.common.web;

import java.io.IOException;

import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Correlación obligatoria en TODA llamada (PLAN §4.5).
 * Los filtros corren ANTES del DispatcherServlet: aquí el problema RFC 7807 se
 * escribe directamente en la respuesta (no aplica @RestControllerAdvice).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    private final RequestIdProperties props;
    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String incoming = req.getHeader(HEADER);
        log.info("[RequestIdFilter] uri={} incoming={}", req.getRequestURI(), incoming);
        try {
            if (incoming == null || incoming.isBlank()) {
                if (props.mode() == RequestIdProperties.Mode.STRICT) {
                    writeProblem(res, req, ErrorCode.FALTA_REQUEST_ID, new Object[0]);
                    return;
                }
                incoming = UUID.randomUUID().toString();
            } else {
                try {
                    UUID.fromString(incoming);
                } catch (IllegalArgumentException e) {
                    writeProblem(res, req, ErrorCode.REQUEST_ID_INVALIDO, new Object[] { incoming });
                    return;
                }
            }
            MDC.put("requestId", incoming);
            res.setHeader(HEADER, incoming); // antes de la cadena: si ya se comiteó la respuesta, el header se perdería
            chain.doFilter(req, res);
        } finally {
            MDC.remove("requestId");
        }
    }

    private void writeProblem(HttpServletResponse res, HttpServletRequest req,
            ErrorCode code, Object[] args) throws IOException {
        String msg = messages.getMessage(code.key(), args, code.name(), resolveLocale(req));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", msg);
        body.put("instance", req.getRequestURI());
        res.setStatus(code.http().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader(HEADER, UUID.randomUUID().toString()); // respuesta siempre correlacionada
        objectMapper.writeValue(res.getOutputStream(), body);
        log.warn("Request rechazado por {}: uri={}", code, req.getRequestURI());
    }

    /**
     * Default es-MX fijo (PLAN §4.4); Accept-Language del request puede forzar en.
     */
    public static Locale resolveLocale(HttpServletRequest req) {
        String al = req.getHeader("Accept-Language");
        if (al != null && al.toLowerCase(java.util.Locale.ROOT).startsWith("en")) {
            return Locale.ENGLISH;
        }
        return Locale.of("es", "MX");
    }
}
