package mx.ferreteria.api.common.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.web.RequestIdFilter;

/** 401 envelope para rutas autenticadas sin token válido (PLAN §5/§4.6). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException ex) throws IOException, ServletException {
        ErrorCode code = ErrorCode.TOKEN_EXPIRADO;
        String msg = messages.getMessage(code.key(), new Object[0], code.name(),
                RequestIdFilter.resolveLocale(req));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", msg);
        body.put("requestId", req.getHeader(RequestIdFilter.HEADER));
        body.put("instance", req.getRequestURI());

        res.setStatus(code.http().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader(RequestIdFilter.HEADER, req.getHeader(RequestIdFilter.HEADER));
        objectMapper.writeValue(res.getOutputStream(), body);
        log.warn("Request rechazado por {}: uri={}", code, req.getRequestURI());
    }
}
