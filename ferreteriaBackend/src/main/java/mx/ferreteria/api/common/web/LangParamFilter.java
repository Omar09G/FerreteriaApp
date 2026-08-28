package mx.ferreteria.api.common.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Filtro servlet que valida el query param {@code ?lang=es|en} en TODA llamada
 * a {@code /api/**} (PLAN M7.1). Corre inmediatamente despues de
 * {@link RequestIdFilter} para que el requestId ya este correlacionado cuando
 * se rechaza por idioma invalido.
 *
 * <p>Comportamiento:
 * <ul>
 *   <li>Sin query param o vacio: deja pasar (locale por Accept-Language o default).</li>
 *   <li>{@code ?lang=es|en} (case-insensitive): deja pasar y publica el locale resuelto
 *       como atributo de request ({@link LocaleResolver#ATTR_LOCALE}) y en MDC.</li>
 *   <li>Valor invalido: responde 400 con el sobre estandar
 *       ({@link ErrorCode#IDIOMA_INVALIDO}) y el mensaje localizado en el locale por default
 *       (porque el usuario no nos dijo en que idioma quiere el error).</li>
 *   <li>OPTIONS (preflight CORS): se ignora — el filtro CORS de Spring Security corta antes.</li>
 *   <li>Paths fuera de {@code /api/**}: se ignoran (actuator, swagger, etc.).</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LangParamFilter extends OncePerRequestFilter {

    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    public LangParamFilter(MessageSource messages, ObjectMapper objectMapper) {
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        LocaleResolver.Validation v = LocaleResolver.validateLangParam(req);
        if (!v.valid()) {
            writeInvalid(req, res, v.invalidValue());
            return;
        }
        req.setAttribute(LocaleResolver.ATTR_LOCALE, v.resolved());
        MDC.put("locale", v.resolved().toLanguageTag());
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("locale");
        }
    }

    private void writeInvalid(HttpServletRequest req, HttpServletResponse res, String invalidValue) throws IOException {
        ErrorCode code = ErrorCode.IDIOMA_INVALIDO;
        String msg = messages.getMessage(code.key(), new Object[] { invalidValue }, code.name(), LocaleResolver.DEFAULT_LOCALE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", msg);
        body.put("instance", req.getRequestURI());
        res.setStatus(code.http().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader(RequestIdFilter.HEADER, UUID.randomUUID().toString());
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
