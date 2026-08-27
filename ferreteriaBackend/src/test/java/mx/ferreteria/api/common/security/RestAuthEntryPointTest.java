package mx.ferreteria.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RestAuthEntryPointTest {

    private RestAuthEntryPoint entryPoint;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        entryPoint = new RestAuthEntryPoint(messages, mapper);
    }

    private JsonNode commenceBody(String acceptLanguage) throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/v1/productos");
        if (acceptLanguage != null) {
            req.addHeader("Accept-Language", acceptLanguage);
        }
        var res = new MockHttpServletResponse();
        entryPoint.commence(req, res, new AuthenticationServiceException("sin token"));
        return mapper.readTree(res.getContentAsString());
    }

    @Test
    @DisplayName("401 envelope con codigo TOKEN_EXPIRADO en espanol por defecto")
    void responds401_envelope_spanishDefault() throws Exception {
        JsonNode body = commenceBody(null);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("errorCode").asInt()).isEqualTo(401);
        assertThat(body.path("codigo").asText()).isEqualTo("TOKEN_EXPIRADO");
        assertThat(body.path("errorMessage").asText()).startsWith("La sesion expiro");
    }

    @Test
    @DisplayName("Accept-Language: en cambia el mensaje; default es-MX se mantiene")
    void localeSwitching() throws Exception {
        assertThat(commenceBody("en").path("errorMessage").asText()).startsWith("Session expired");
        assertThat(commenceBody(null).path("errorMessage").asText()).startsWith("La sesion expiro");
    }
}
