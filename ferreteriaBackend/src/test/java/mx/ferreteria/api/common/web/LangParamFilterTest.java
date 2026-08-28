package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;

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

class LangParamFilterTest {

    private ObjectMapper mapper;
    private ResourceBundleMessageSource messages;
    private LangParamFilter filter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        filter = new LangParamFilter(messages, mapper);
    }

    private JsonNode body(MockHttpServletResponse res) {
        try {
            return mapper.readTree(res.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Sin ?lang: deja pasar, expone locale por header en atributo")
    void sinLang_pasa() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(LocaleResolver.ATTR_LOCALE)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    @DisplayName("?lang=es valido: deja pasar y publica es-MX")
    void langEs_pasa() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addParameter("lang", "es");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(LocaleResolver.ATTR_LOCALE)).isEqualTo(LocaleResolver.DEFAULT_LOCALE);
    }

    @Test
    @DisplayName("?lang=en valido: deja pasar y publica en")
    void langEn_pasa() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addParameter("lang", "en");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(LocaleResolver.ATTR_LOCALE)).isEqualTo(java.util.Locale.ENGLISH);
    }

    @Test
    @DisplayName("?lang=fr invalido: 400 IDIOMA_INVALIDO con envelope y cadena NO invocada")
    void langInvalido_400() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addParameter("lang", "fr");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(res.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        JsonNode body = body(res);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("errorCode").asInt()).isEqualTo(400);
        assertThat(body.path("codigo").asText()).isEqualTo("IDIOMA_INVALIDO");
        assertThat(body.path("errorMessage").asText()).contains("fr").contains("es").contains("en");
    }

    @Test
    @DisplayName("OPTIONS (preflight) se ignora: no valida lang")
    void optionsSeIgnora() throws Exception {
        var req = new MockHttpServletRequest("OPTIONS", "/api/v1/productos");
        req.addParameter("lang", "fr");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Paths fuera de /api/**: no valida lang")
    void pathNoApiNoValida() throws Exception {
        var req = new MockHttpServletRequest("GET", "/actuator/health");
        req.addParameter("lang", "fr");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("?lang vacio: pasa como ausente")
    void langVacio_pasa() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        req.addParameter("lang", "");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
