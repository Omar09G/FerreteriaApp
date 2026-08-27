package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RequestIdFilterTest {

    private ObjectMapper mapper;
    private ResourceBundleMessageSource messages;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
    }

    private RequestIdFilter filter(RequestIdProperties.Mode mode) {
        return new RequestIdFilter(new RequestIdProperties(mode), messages, mapper);
    }

    @Test
    @DisplayName("GENERATE sin header: genera UUID v4, lo ecoa y deja pasar la cadena")
    void generateMode_missingHeader_generatesUuidAndPassesThrough() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter(RequestIdProperties.Mode.GENERATE).doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        String echoed = res.getHeader(RequestIdFilter.HEADER);
        assertThat(echoed).isNotBlank();
        UUID.fromString(echoed);
    }

    @Test
    @DisplayName("GENERATE con UUID valido: se respeta tal cual (eco identico)")
    void generateMode_validHeader_isRespectedVerbatim() throws Exception {
        String fixed = "123e4567-e89b-12d3-a456-426614174000";
        var req = new MockHttpServletRequest("GET", "/x");
        req.addHeader(RequestIdFilter.HEADER, fixed);
        var res = new MockHttpServletResponse();

        filter(RequestIdProperties.Mode.GENERATE).doFilter(req, res, new MockFilterChain());

        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(fixed);
    }

    @Test
    @DisplayName("Header invalido: 400 REQUEST_ID_INVALIDO con envelope y cadena NO invocada")
    void invalidHeader_rejectsWith400_andDoesNotCallChain() throws Exception {
        var req = new MockHttpServletRequest("GET", "/x");
        req.addHeader(RequestIdFilter.HEADER, "no-es-un-uuid");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        assertThatCode(() ->
                filter(RequestIdProperties.Mode.GENERATE).doFilter(req, res, chain))
                .doesNotThrowAnyException();

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = readBody(res);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("errorCode").asInt()).isEqualTo(400);
        assertThat(body.path("codigo").asText()).isEqualTo("REQUEST_ID_INVALIDO");
        assertThat(body.path("errorMessage").asText()).contains("no-es-un-uuid");
    }

    @Test
    @DisplayName("STRICT sin header: 400 FALTA_REQUEST_ID y la respuesta queda correlacionada")
    void strictMode_missingHeader_rejects400() throws Exception {
        var req = new MockHttpServletRequest("GET", "/x");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter(RequestIdProperties.Mode.STRICT).doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(400);
        JsonNode body = readBody(res);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("codigo").asText()).isEqualTo("FALTA_REQUEST_ID");
        assertThat(body.path("errorMessage").asText())
                .as("default es-MX aunque la JVM este en otro locale")
                .startsWith("Falta el header X-Request-Id");
        assertThat(res.getHeader(RequestIdFilter.HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("Accept-Language: en fuerza mensaje ingles; default sigue siendo espanol")
    void acceptLanguage_switchesBundle() throws Exception {
        var req = new MockHttpServletRequest("GET", "/x");
        req.addHeader("Accept-Language", "en-US,en;q=0.9");
        var res = new MockHttpServletResponse();

        filter(RequestIdProperties.Mode.STRICT).doFilter(req, res, new MockFilterChain());

        JsonNode body = readBody(res);
        assertThat(body.path("errorMessage").asText()).startsWith("Missing X-Request-Id header");
        assertThat(body.path("codigo").asText()).isEqualTo("FALTA_REQUEST_ID");
    }

    private JsonNode readBody(MockHttpServletResponse res) {
        try {
            return mapper.readTree(res.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
