package mx.ferreteria.api.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Envuelve respuestas exitosas en {success:true, data, meta?} (PLAN §4.6).
 * Respuestas de error (Map con "success") pasan sin envolver.
 * Los filtros (RequestIdFilter, RestAuthEntryPoint) escriben directo al stream,
 * por lo que no pasan por este advice.
 */
@ControllerAdvice
public class EnvelopeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof Map<?, ?> m && m.containsKey("success")) {
            return body;
        }
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("success", true);
        if (body instanceof org.springframework.data.domain.Page<?> page) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("page", page.getNumber());
            meta.put("size", page.getSize());
            meta.put("totalElements", page.getTotalElements());
            meta.put("totalPages", page.getTotalPages());
            envelope.put("meta", meta);
            envelope.put("data", page.getContent());
        } else {
            envelope.put("data", body);
        }
        return envelope;
    }
}
