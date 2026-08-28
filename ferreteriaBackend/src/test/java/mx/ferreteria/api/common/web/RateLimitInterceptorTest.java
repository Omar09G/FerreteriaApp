package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RateLimitInterceptorTest {

    private ObjectMapper mapper;
    private ResourceBundleMessageSource messages;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitInterceptor interceptor(RateLimitProperties p) {
        return new RateLimitInterceptor(p, messages, mapper);
    }

    private HandlerMethod handlerConPerfil(Class<?> controller, String methodName) throws Exception {
        Method m = controller.getDeclaredMethod(methodName);
        return new HandlerMethod(controller.getDeclaredConstructor().newInstance(), m);
    }

    private MockHttpServletRequest get(String uri) {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr("203.0.113.7");
        return req;
    }

    private JsonNode body(MockHttpServletResponse res) {
        try {
            return mapper.readTree(res.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RateLimitProperties propsDefault() {
        return new RateLimitProperties(
                true,
                true,
                10_000L,
                5L,
                Map.of(
                        "default", new RateLimitProperties.Grupo(2, 1),
                        "catalogo", new RateLimitProperties.Grupo(2, 1),
                        "auth", new RateLimitProperties.Grupo(1, 1)));
    }

    @Test
    @DisplayName("Disabled=true: no consume, deja pasar y no agrega cabeceras")
    void disabled_passesThroughWithoutConsuming() throws Exception {
        RateLimitProperties off = new RateLimitProperties(false, true, 1000L, 5L, propsDefault().grupos());
        var req = get("/api/v1/productos");
        var res = new MockHttpServletResponse();
        boolean allow = interceptor(off).preHandle(req, res, handlerConPerfil(MarcadorController.class, "catalogo"));

        assertThat(allow).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(res.getHeader("X-RateLimit-Remaining")).isNull();
    }

    @Test
    @DisplayName("OPTIONS (preflight CORS) pasa sin consumir token")
    void optionsPassesThrough() throws Exception {
        var req = new MockHttpServletRequest("OPTIONS", "/api/v1/productos");
        var res = new MockHttpServletResponse();
        boolean allow = interceptor(propsDefault()).preHandle(req, res, handlerConPerfil(MarcadorController.class, "catalogo"));

        assertThat(allow).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    @DisplayName("Handler != HandlerMethod (recurso estatico) pasa sin consumir")
    void nonHandlerMethodPassesThrough() throws Exception {
        var req = get("/api/v1/productos");
        var res = new MockHttpServletResponse();
        boolean allow = interceptor(propsDefault()).preHandle(req, res, new Object());

        assertThat(allow).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    @DisplayName("Rafaga=2: 1ª OK remaining=1; 2ª OK remaining=0; 3ª 429 con envelope y cabeceras")
    void consumesThenRejects429WithEnvelopeAndHeaders() throws Exception {
        RateLimitInterceptor itc = interceptor(propsDefault());
        HandlerMethod hm = handlerConPerfil(MarcadorController.class, "catalogo");

        var res1 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), res1, hm)).isTrue();
        assertThat(res1.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(res1.getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        var res2 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), res2, hm)).isTrue();
        assertThat(res2.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        var res3 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), res3, hm)).isFalse();
        assertThat(res3.getStatus()).isEqualTo(429);
        assertThat(res3.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(res3.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(res3.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(res3.getHeader("Retry-After")).isNotNull();

        JsonNode body = body(res3);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("errorCode").asInt()).isEqualTo(429);
        assertThat(body.path("codigo").asText()).isEqualTo("LIMITE_VELOCIDAD_EXCEDIDO");
        assertThat(body.path("errorMessage").asText()).contains("Demasiadas peticiones");
        assertThat(body.path("instance").asText()).isEqualTo("/api/v1/productos");
    }

    @Test
    @DisplayName("Perfil del metodo sobreescribe al de la clase")
    void methodAnnotationOverridesClassAnnotation() throws Exception {
        var req = get("/api/v1/otro");
        var res = new MockHttpServletResponse();
        boolean allow = interceptor(propsDefault()).preHandle(req, res, handlerConPerfil(MarcadorController.class, "mixto"));

        assertThat(allow).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("1");
    }

    @Test
    @DisplayName("Sin anotacion usa el perfil default")
    void sinAnotacionUsaDefault() throws Exception {
        var req = get("/api/v1/otro");
        var res = new MockHttpServletResponse();
        boolean allow = interceptor(propsDefault()).preHandle(req, res, handlerConPerfil(MarcadorController.class, "neutro"));

        assertThat(allow).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("2");
    }

    @Test
    @DisplayName("scopeByUser=true con usuario autenticado: claves separadas por usuario")
    void clavesSeparadasPorUsuarioCuandoAutenticado() throws Exception {
        RateLimitInterceptor itc = interceptor(propsDefault());
        HandlerMethod hm = handlerConPerfil(MarcadorController.class, "catalogo");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new mx.ferreteria.api.common.security.UserPrincipal(42, "alice", 1, List.of("VENDEDOR")),
                        null,
                        List.of()));
        var resA = new MockHttpServletResponse();
        itc.preHandle(get("/api/v1/productos"), resA, hm);
        assertThat(resA.getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new mx.ferreteria.api.common.security.UserPrincipal(99, "bob", 1, List.of("VENDEDOR")),
                        null,
                        List.of()));
        var resB = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), resB, hm)).isTrue();
        assertThat(resB.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
    }

    @Test
    @DisplayName("scopeByUser=false: siempre cae a IP")
    void scopeByUserFalseUsaIp() throws Exception {
        RateLimitInterceptor itc = interceptor(new RateLimitProperties(true, false, 1000L, 5L, propsDefault().grupos()));
        HandlerMethod hm = handlerConPerfil(MarcadorController.class, "catalogo");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new mx.ferreteria.api.common.security.UserPrincipal(1, "u", 1, List.of("VENDEDOR")),
                        null,
                        List.of()));
        var res1 = new MockHttpServletResponse();
        itc.preHandle(get("/api/v1/productos"), res1, hm);
        assertThat(res1.getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        var res2 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), res2, hm)).isTrue();
        assertThat(res2.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("IP se toma del primer hop de X-Forwarded-For si esta presente")
    void ipDesdeXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.1");
        assertThat(RateLimitInterceptor.ipCliente(req)).isEqualTo("198.51.100.10");

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/productos");
        req2.setRemoteAddr("127.0.0.1");
        assertThat(RateLimitInterceptor.ipCliente(req2)).isEqualTo("127.0.0.1");

        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/api/v1/productos");
        req3.addHeader("X-Forwarded-For", "");
        req3.setRemoteAddr("127.0.0.1");
        assertThat(RateLimitInterceptor.ipCliente(req3)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Perfil inexistente cae al grupo default del yaml")
    void perfilInexistenteCaeADefault() throws Exception {
        RateLimitInterceptor itc = interceptor(new RateLimitProperties(true, true, 1000L, 5L,
                Map.of("default", new RateLimitProperties.Grupo(2, 1))));
        HandlerMethod hm = handlerConPerfil(MarcadorController.class, "catalogo");

        var res = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/productos"), res, hm)).isTrue();
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("2");
    }

    @Test
    @DisplayName("Mensaje en ingles cuando Accept-Language: en")
    void mensajeInglesConAcceptLanguageEn() throws Exception {
        RateLimitInterceptor itc = interceptor(propsDefault());
        HandlerMethod hm = handlerConPerfil(MarcadorController.class, "catalogo");

        itc.preHandle(reqIngles("/api/v1/productos"), new MockHttpServletResponse(), hm);
        itc.preHandle(reqIngles("/api/v1/productos"), new MockHttpServletResponse(), hm);
        var res3 = new MockHttpServletResponse();
        assertThat(itc.preHandle(reqIngles("/api/v1/productos"), res3, hm)).isFalse();
        JsonNode body = body(res3);
        assertThat(body.path("errorMessage").asText()).startsWith("Too many requests");
    }

    private MockHttpServletRequest reqIngles(String uri) {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr("203.0.113.7");
        req.addHeader("Accept-Language", "en-US,en;q=0.9");
        return req;
    }

    @RateLimited("catalogo")
    static class MarcadorController {
        MarcadorController() {
        }

        @SuppressWarnings("unused")
        public void catalogo() {
        }

        @RateLimited("auth")
        @SuppressWarnings("unused")
        public void mixto() {
        }

        @SuppressWarnings("unused")
        public void neutro() {
        }
    }

    @RateLimited("catalogo")
    static class OtroMarcadorController {
        OtroMarcadorController() {
        }

        @SuppressWarnings("unused")
        public void catalogo() {
        }
    }

    @Test
    @DisplayName("Dos controllers del mismo perfil tienen buckets separados (sin bloqueo en cascada)")
    void dosControllersDelMismoPerfilTienenBucketsSeparados() throws Exception {
        // capacidad=2 por controller: agotar MarcadorController NO debe afectar a OtroMarcadorController
        RateLimitInterceptor itc = interceptor(propsDefault());
        HandlerMethod hmMarca = handlerConPerfil(MarcadorController.class, "catalogo");
        HandlerMethod hmOtro = handlerConPerfil(OtroMarcadorController.class, "catalogo");

        // Agotamos MarcadorController
        itc.preHandle(get("/api/v1/marcas"), new MockHttpServletResponse(), hmMarca);
        itc.preHandle(get("/api/v1/marcas"), new MockHttpServletResponse(), hmMarca);
        var res3 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/marcas"), res3, hmMarca)).isFalse();
        assertThat(res3.getStatus()).isEqualTo(429);

        // OtroMarcadorController sigue teniendo su rafaga completa
        var resOtro1 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/otros"), resOtro1, hmOtro)).isTrue();
        assertThat(resOtro1.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        var resOtro2 = new MockHttpServletResponse();
        assertThat(itc.preHandle(get("/api/v1/otros"), resOtro2, hmOtro)).isTrue();
        assertThat(resOtro2.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("controllerId usa el FQN del bean del controller")
    void controllerIdUsaFqnDelBean() throws Exception {
        HandlerMethod hmMarca = handlerConPerfil(MarcadorController.class, "catalogo");
        HandlerMethod hmOtro = handlerConPerfil(OtroMarcadorController.class, "catalogo");

        assertThat(RateLimitInterceptor.controllerId(hmMarca))
                .isEqualTo(MarcadorController.class.getName())
                .isNotEqualTo(RateLimitInterceptor.controllerId(hmOtro));
    }
}
