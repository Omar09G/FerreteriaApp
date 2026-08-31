package mx.ferreteria.api.common.web;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;

/**
 * Rate limit por controller (PLAN M7) usando bucket4j en memoria (Caffeine).
 * <p>
 * Aislamiento por controller (clave del bucket:
 * {@code perfil:controllerClassName:userOrIp}). Esto evita el bloqueo en
 * cascada: si un controller agota su rafaga, los demas controllers del mismo
 * perfil siguen disponibles porque cada uno tiene su propio bucket. El perfil
 * determina la capacidad y el ritmo de recarga; el controller determina la
 * identidad del bucket.
 *
 * <p>
 * Resolucion de clave:
 * <ul>
 * <li>Si el usuario esta autenticado y {@code scopeByUser=true}:
 * {@code u<uid>}.</li>
 * <li>Si no: {@code ip:<xff-or-remote>} (primer hop de {@code X-Forwarded-For}
 * o, en su defecto, {@link HttpServletRequest#getRemoteAddr()}).</li>
 * </ul>
 *
 * <p>
 * Si la rafaga se agota responde 429 con el sobre estandar (§4.6) y cabeceras
 * {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining} y
 * {@code Retry-After}
 * (segundos derivados del tiempo de espera para la siguiente recarga).
 *
 * <p>
 * <b>Por que NO es {@code @Component}:</b> el filtro de tipo
 * {@code WebMvcTypeExcludeFilter} de {@code @WebMvcTest} escanea clases que
 * implementan {@code HandlerInterceptor}; marcarlas como componentes rompe los
 * slices. Por eso se instancia manualmente desde {@code RateLimitConfig}.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties props;
    private final MessageSource messages;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets;

    public RateLimitInterceptor(RateLimitProperties props, MessageSource messages, ObjectMapper objectMapper) {
        this.props = props;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(Math.max(1L, props.cacheMaxSize()))
                .expireAfterAccess(Duration.ofMinutes(Math.max(1L, props.cacheTtlMinutes())))
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!props.enabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        String perfil = perfilDe(hm);
        RateLimitProperties.Grupo grupo = props.grupo(perfil);
        String controllerId = controllerId(hm);
        String usuarioOIp = claveDe(request);
        String clave = perfil + ":" + controllerId + ":" + usuarioOIp;
        Bucket bucket = buckets.get(clave, k -> nuevoBucket(grupo));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", Integer.toString(grupo.capacity()));

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            return true;
        }

        long retrySeconds = segundosDeEspera(probe);
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After", Long.toString(retrySeconds));
        escribirRechazo(request, response, retrySeconds);
        return false;
    }

    static String perfilDe(HandlerMethod hm) {
        RateLimited method = hm.getMethodAnnotation(RateLimited.class);
        if (method != null) {
            return method.value();
        }
        RateLimited type = hm.getBeanType().getAnnotation(RateLimited.class);
        if (type != null) {
            return type.value();
        }
        return "default";
    }

    /**
     * Identidad estable del controller (FQN) para que cada controller tenga su
     * propio bucket. Dos controllers con el mismo {@code @RateLimited("perfil")}
     * NO comparten bucket: solo comparten la capacidad y el ritmo de recarga.
     */
    static String controllerId(HandlerMethod hm) {
        return hm.getBeanType().getName();
    }

    private String claveDe(HttpServletRequest req) {
        if (props.scopeByUser()) {
            Optional<Integer> uid = usuarioActualId();
            if (uid.isPresent()) {
                return "u" + uid.get();
            }
        }
        return "ip:" + ipCliente(req);
    }

    private static Optional<Integer> usuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return Optional.of(up.usuarioId());
        }
        return Optional.empty();
    }

    static String ipCliente(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static Bucket nuevoBucket(RateLimitProperties.Grupo g) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(Math.max(1L, g.capacity()))
                .refillGreedy(Math.max(1L, g.refillPerSecond()), Duration.ofSeconds(1))
                .build();

        return Bucket.builder().addLimit(limit).build();
    }

    private static long segundosDeEspera(ConsumptionProbe probe) {
        long nanos = probe.getNanosToWaitForRefill();
        long segundos = nanos / 1_000_000_000L;
        return Math.max(1L, segundos + 1L);
    }

    private void escribirRechazo(HttpServletRequest req, HttpServletResponse res, long retrySeconds)
            throws IOException {
        ErrorCode code = ErrorCode.LIMITE_VELOCIDAD_EXCEDIDO;
        String msg = messages.getMessage(code.key(), new Object[] { retrySeconds }, code.name(),
                LocaleResolver.resolve(req));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("data", null);
        body.put("errorCode", code.http().value());
        body.put("codigo", code.name());
        body.put("errorMessage", msg);
        body.put("instance", req.getRequestURI());
        res.setStatus(code.http().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
